/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence

import org.apache.pekko
import pekko.actor.{ ActorLogging, ActorRef, Props }
import pekko.testkit.ImplicitSender

object SnapshotRecoveryLocalStoreSpec {
  val persistenceId = "europe"
  val extendedName = persistenceId + "italy"

  case object TakeSnapshot

  class SaveSnapshotTestPersistentActor(name: String, probe: ActorRef) extends NamedPersistentActor(name) {
    var state = s"State for actor $name"
    def receiveCommand = {
      case TakeSnapshot            => saveSnapshot(state)
      case SaveSnapshotSuccess(md) => probe ! md.sequenceNr
      case GetState                => probe ! state
    }
    def receiveRecover = {
      case _ =>
    }
  }

  class LoadSnapshotTestPersistentActor(name: String, probe: ActorRef)
      extends NamedPersistentActor(name)
      with ActorLogging {

    override def recovery = Recovery(toSequenceNr = 0)

    def receiveCommand = {
      case _ =>
    }
    def receiveRecover = {
      case other => probe ! other
    }
  }
}

class SnapshotRecoveryLocalStoreSpec
    extends PersistenceSpec(PersistenceSpec.config("inmem", "SnapshotRecoveryLocalStoreSpec"))
    with ImplicitSender {

  import SnapshotRecoveryLocalStoreSpec._

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    val persistentActor1 = system.actorOf(Props(classOf[SaveSnapshotTestPersistentActor], persistenceId, testActor))
    val persistentActor2 = system.actorOf(Props(classOf[SaveSnapshotTestPersistentActor], extendedName, testActor))
    persistentActor1 ! TakeSnapshot
    persistentActor2 ! TakeSnapshot
    expectMsgAllOf(0L, 0L)
  }

  "A persistent actor which is persisted at the same time as another actor whose persistenceId is an extension of the first " must {
    "recover state only from its own correct snapshot file" in {

      system.actorOf(Props(classOf[LoadSnapshotTestPersistentActor], persistenceId, testActor))

      expectMsgPF() { case SnapshotOffer(SnapshotMetadata(`persistenceId`, _, _), _) => }
      expectMsg(RecoveryCompleted)
    }

  }
}
