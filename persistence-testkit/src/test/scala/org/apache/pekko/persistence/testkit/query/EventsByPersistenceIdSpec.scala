/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.query

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }
import pekko.actor.typed.ActorRef
import pekko.persistence.query.{ EventEnvelope, PersistenceQuery }
import pekko.persistence.testkit.PersistenceTestKitPlugin
import pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }
import pekko.stream.testkit.scaladsl.TestSink
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

object EventsByPersistenceIdSpec {
  val config = PersistenceTestKitPlugin.config.withFallback(
    ConfigFactory.parseString("""
    pekko.loglevel = DEBUG
    pekko.loggers = ["org.apache.pekko.testkit.SilenceAllTestEventListener"]
    pekko.persistence.testkit.events.serialize = off
      """))

  case class Command(evt: String, ack: ActorRef[Done])
  case class State()

  def testBehaviour(persistenceId: String) = {
    EventSourcedBehavior[Command, String, State](
      PersistenceId.ofUniqueId(persistenceId),
      State(),
      (_, command) =>
        Effect.persist(command.evt).thenRun { _ =>
          command.ack ! Done
        },
      (state, _) => state)
  }

}

class EventsByPersistenceIdSpec
    extends ScalaTestWithActorTestKit(EventsByPersistenceIdSpec.config)
    with LogCapturing
    with AnyWordSpecLike {
  import EventsByPersistenceIdSpec._

  implicit val classic: pekko.actor.ActorSystem = system.classicSystem

  val queries =
    PersistenceQuery(system).readJournalFor[PersistenceTestKitReadJournal](PersistenceTestKitReadJournal.Identifier)

  def setup(persistenceId: String): ActorRef[Command] = {
    val probe = createTestProbe[Done]()
    val ref = setupEmpty(persistenceId)
    ref ! Command(s"$persistenceId-1", probe.ref)
    ref ! Command(s"$persistenceId-2", probe.ref)
    ref ! Command(s"$persistenceId-3", probe.ref)
    probe.expectMessage(Done)
    probe.expectMessage(Done)
    probe.expectMessage(Done)
    ref
  }

  def setupEmpty(persistenceId: String): ActorRef[Command] = {
    spawn(testBehaviour(persistenceId))
  }

  "Persistent test kit live query EventsByPersistenceId" must {
    "find new events" in {
      val ackProbe = createTestProbe[Done]()
      val ref = setup("c")
      val src = queries.eventsByPersistenceId("c", 0L, Long.MaxValue)
      val probe = src.map(_.event).runWith(TestSink.probe[Any]).request(5).expectNext("c-1", "c-2", "c-3")

      ref ! Command("c-4", ackProbe.ref)
      ackProbe.expectMessage(Done)

      probe.expectNext("c-4")
    }

    "find new events up to a sequence number" in {
      val ackProbe = createTestProbe[Done]()
      val ref = setup("d")
      val src = queries.eventsByPersistenceId("d", 0L, 4L)
      val probe = src.map(_.event).runWith(TestSink.probe[Any]).request(5).expectNext("d-1", "d-2", "d-3")

      ref ! Command("d-4", ackProbe.ref)
      ackProbe.expectMessage(Done)

      probe.expectNext("d-4").expectComplete()
    }

    "find new events after demand request" in {
      val ackProbe = createTestProbe[Done]()
      val ref = setup("e")
      val src = queries.eventsByPersistenceId("e", 0L, Long.MaxValue)
      val probe =
        src.map(_.event).runWith(TestSink.probe[Any]).request(2).expectNext("e-1", "e-2").expectNoMessage(100.millis)

      ref ! Command("e-4", ackProbe.ref)
      ackProbe.expectMessage(Done)

      probe.expectNoMessage(100.millis).request(5).expectNext("e-3").expectNext("e-4")
    }

    "include timestamp in EventEnvelope" in {
      setup("n")

      val src = queries.eventsByPersistenceId("n", 0L, Long.MaxValue)
      val probe = src.runWith(TestSink.probe[EventEnvelope])

      probe.request(5)
      probe.expectNext().timestamp should be > 0L
      probe.expectNext().timestamp should be > 0L
      probe.cancel()
    }

    "not complete for empty persistence id" in {
      val ackProbe = createTestProbe[Done]()
      val src = queries.eventsByPersistenceId("o", 0L, Long.MaxValue)
      val probe =
        src.map(_.event).runWith(TestSink.probe[Any]).request(2)

      probe.expectNoMessage(200.millis) // must not complete

      val ref = setupEmpty("o")
      ref ! Command("o-1", ackProbe.ref)
      ackProbe.expectMessage(Done)

      probe.cancel()
    }
  }
}
