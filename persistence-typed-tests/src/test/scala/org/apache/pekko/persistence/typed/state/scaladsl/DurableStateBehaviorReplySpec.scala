/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.state.scaladsl

import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.typed.PersistenceId
import pekko.serialization.jackson.CborSerializable

import pekko.persistence.testkit.PersistenceTestKitDurableStateStorePlugin

object DurableStateBehaviorReplySpec {
  def conf: Config = PersistenceTestKitDurableStateStorePlugin.config.withFallback(ConfigFactory.parseString(s"""
    pekko.loglevel = INFO
    """))

  sealed trait Command[ReplyMessage] extends CborSerializable
  final case class IncrementWithConfirmation(replyTo: ActorRef[Done]) extends Command[Done]
  final case class IncrementReplyLater(replyTo: ActorRef[Done]) extends Command[Done]
  final case class ReplyNow(replyTo: ActorRef[Done]) extends Command[Done]
  final case class GetValue(replyTo: ActorRef[State]) extends Command[State]
  final case class DeleteWithConfirmation(replyTo: ActorRef[Done]) extends Command[Done]
  case object Increment extends Command[Nothing]
  case class IncrementBy(by: Int) extends Command[Nothing]

  final case class State(value: Int) extends CborSerializable

  def counter(persistenceId: PersistenceId): Behavior[Command[_]] =
    Behaviors.setup(ctx => counter(ctx, persistenceId))

  def counter(ctx: ActorContext[Command[_]], persistenceId: PersistenceId): DurableStateBehavior[Command[_], State] = {
    DurableStateBehavior.withEnforcedReplies[Command[_], State](
      persistenceId,
      emptyState = State(0),
      commandHandler = (state, command) =>
        command match {

          case IncrementWithConfirmation(replyTo) =>
            Effect.persist(state.copy(value = state.value + 1)).thenReply(replyTo)(_ => Done)

          case IncrementReplyLater(replyTo) =>
            Effect
              .persist(state.copy(value = state.value + 1))
              .thenRun((_: State) => ctx.self ! ReplyNow(replyTo))
              .thenNoReply()

          case ReplyNow(replyTo) =>
            Effect.reply(replyTo)(Done)

          case GetValue(replyTo) =>
            Effect.reply(replyTo)(state)

          case DeleteWithConfirmation(replyTo) =>
            Effect.delete[State]().thenReply(replyTo)(_ => Done)

          case _ => ???

        })
  }

}

class DurableStateBehaviorReplySpec
    extends ScalaTestWithActorTestKit(DurableStateBehaviorReplySpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  import DurableStateBehaviorReplySpec._

  val pidCounter = new AtomicInteger(0)
  private def nextPid(): PersistenceId = PersistenceId.ofUniqueId(s"c${pidCounter.incrementAndGet()})")

  "A typed persistent actor with commands that are expecting replies" must {

    "persist state thenReply" in {
      val c = spawn(counter(nextPid()))
      val probe = TestProbe[Done]()
      c ! IncrementWithConfirmation(probe.ref)
      probe.expectMessage(Done)

      c ! IncrementWithConfirmation(probe.ref)
      c ! IncrementWithConfirmation(probe.ref)
      probe.expectMessage(Done)
      probe.expectMessage(Done)
    }

    "persist state thenReply later" in {
      val c = spawn(counter(nextPid()))
      val probe = TestProbe[Done]()
      c ! IncrementReplyLater(probe.ref)
      probe.expectMessage(Done)
    }

    "reply to query command" in {
      val c = spawn(counter(nextPid()))
      val updateProbe = TestProbe[Done]()
      c ! IncrementWithConfirmation(updateProbe.ref)

      val queryProbe = TestProbe[State]()
      c ! GetValue(queryProbe.ref)
      queryProbe.expectMessage(State(1))
    }

    "delete state thenReply" in {
      val c = spawn(counter(nextPid()))
      val updateProbe = TestProbe[Done]()
      c ! IncrementWithConfirmation(updateProbe.ref)
      updateProbe.expectMessage(Done)

      val deleteProbe = TestProbe[Done]()
      c ! DeleteWithConfirmation(deleteProbe.ref)
      deleteProbe.expectMessage(Done)

      val queryProbe = TestProbe[State]()
      c ! GetValue(queryProbe.ref)
      queryProbe.expectMessage(State(0))
    }
  }
}
