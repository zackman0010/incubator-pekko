/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor._
import pekko.testkit.{ EventFilter, ImplicitSender, TestEvent }
import pekko.testkit.TestActors
import pekko.testkit.TestProbe

object RecoveryPermitterSpec {

  class TestExc extends RuntimeException("simulated exc") with NoStackTrace

  def testProps(name: String, probe: ActorRef, throwFromRecoveryCompleted: Boolean = false): Props =
    Props(new TestPersistentActor(name, probe, throwFromRecoveryCompleted))

  class TestPersistentActor(name: String, probe: ActorRef, throwFromRecoveryCompleted: Boolean)
      extends PersistentActor {

    override def persistenceId = name

    override def postStop(): Unit = {
      probe ! "postStop"
    }

    override def receiveRecover: Receive = {
      case RecoveryCompleted =>
        probe ! RecoveryCompleted
        if (throwFromRecoveryCompleted)
          throw new TestExc
    }
    override def receiveCommand: Receive = {
      case "stop" =>
        context.stop(self)
    }
  }

}

class RecoveryPermitterSpec extends PersistenceSpec(ConfigFactory.parseString(s"""
    pekko.persistence.max-concurrent-recoveries = 3
    pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
    pekko.actor.warn-about-java-serializer-usage = off
  """)) with ImplicitSender {
  import RecoveryPermitter._
  import RecoveryPermitterSpec._

  system.eventStream.publish(TestEvent.Mute(EventFilter[TestExc]()))

  val permitter = Persistence(system).recoveryPermitter
  val p1 = TestProbe()
  val p2 = TestProbe()
  val p3 = TestProbe()
  val p4 = TestProbe()
  val p5 = TestProbe()

  def requestPermit(p: TestProbe): Unit = {
    permitter.tell(RequestRecoveryPermit, p.ref)
    p.expectMsg(RecoveryPermitGranted)
  }

  "RecoveryPermitter" must {
    "grant permits up to the limit" in {
      requestPermit(p1)
      requestPermit(p2)
      requestPermit(p3)

      permitter.tell(RequestRecoveryPermit, p4.ref)
      permitter.tell(RequestRecoveryPermit, p5.ref)
      p4.expectNoMessage(100.millis)
      p5.expectNoMessage(10.millis)

      permitter.tell(ReturnRecoveryPermit, p2.ref)
      p4.expectMsg(RecoveryPermitGranted)
      p5.expectNoMessage(100.millis)

      permitter.tell(ReturnRecoveryPermit, p1.ref)
      p5.expectMsg(RecoveryPermitGranted)

      permitter.tell(ReturnRecoveryPermit, p3.ref)
      permitter.tell(ReturnRecoveryPermit, p4.ref)
      permitter.tell(ReturnRecoveryPermit, p5.ref)
    }

    "grant recovery when all permits not used" in {
      requestPermit(p1)

      system.actorOf(testProps("p2", p2.ref))
      p2.expectMsg(RecoveryCompleted)
      permitter.tell(ReturnRecoveryPermit, p1.ref)
    }

    "delay recovery when all permits used" in {
      requestPermit(p1)
      requestPermit(p2)
      requestPermit(p3)

      val persistentActor = system.actorOf(testProps("p4", p4.ref))
      p4.watch(persistentActor)
      persistentActor ! "stop"
      p4.expectNoMessage(200.millis)

      permitter.tell(ReturnRecoveryPermit, p3.ref)
      p4.expectMsg(RecoveryCompleted)
      p4.expectMsg("postStop")
      p4.expectTerminated(persistentActor)

      permitter.tell(ReturnRecoveryPermit, p1.ref)
      permitter.tell(ReturnRecoveryPermit, p2.ref)
    }

    "return permit when actor is pre-maturely terminated before holding permit" in {
      requestPermit(p1)
      requestPermit(p2)
      requestPermit(p3)

      val persistentActor = system.actorOf(testProps("p4", p4.ref))
      p4.expectNoMessage(100.millis)

      permitter.tell(RequestRecoveryPermit, p5.ref)
      p5.expectNoMessage(100.millis)

      // PoisonPill is not stashed
      persistentActor ! PoisonPill
      p4.expectMsg("postStop")

      // persistentActor didn't hold a permit so still
      p5.expectNoMessage(100.millis)

      permitter.tell(ReturnRecoveryPermit, p1.ref)
      p5.expectMsg(RecoveryPermitGranted)

      permitter.tell(ReturnRecoveryPermit, p2.ref)
      permitter.tell(ReturnRecoveryPermit, p3.ref)
      permitter.tell(ReturnRecoveryPermit, p5.ref)
    }

    "return permit when actor is pre-maturely terminated when holding permit" in {
      val actor = system.actorOf(TestActors.forwardActorProps(p1.ref))
      permitter.tell(RequestRecoveryPermit, actor)
      p1.expectMsg(RecoveryPermitGranted)

      requestPermit(p2)
      requestPermit(p3)

      permitter.tell(RequestRecoveryPermit, p4.ref)
      p4.expectNoMessage(100.millis)

      actor ! PoisonPill
      p4.expectMsg(RecoveryPermitGranted)

      permitter.tell(ReturnRecoveryPermit, p2.ref)
      permitter.tell(ReturnRecoveryPermit, p3.ref)
      permitter.tell(ReturnRecoveryPermit, p4.ref)
    }

    "return permit when actor throws from RecoveryCompleted" in {
      requestPermit(p1)
      requestPermit(p2)

      val persistentActor = system.actorOf(testProps("p3", p3.ref, throwFromRecoveryCompleted = true))
      p3.expectMsg(RecoveryCompleted)
      p3.expectMsg("postStop")
      // it's restarting
      (1 to 5).foreach { _ =>
        p3.expectMsg(RecoveryCompleted)
        p3.expectMsg("postStop")
      }
      // stop it
      val stopProbe = TestProbe()
      stopProbe.watch(persistentActor)
      system.stop(persistentActor)
      stopProbe.expectTerminated(persistentActor)

      requestPermit(p4)

      permitter.tell(ReturnRecoveryPermit, p1.ref)
      permitter.tell(ReturnRecoveryPermit, p2.ref)
      permitter.tell(ReturnRecoveryPermit, p4.ref)
    }
  }

}
