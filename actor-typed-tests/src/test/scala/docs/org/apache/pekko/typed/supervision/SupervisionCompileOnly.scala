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

package docs.org.apache.pekko.typed.supervision

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.PostStop
import pekko.actor.typed.PreRestart
import pekko.actor.typed.{ Behavior, SupervisorStrategy }
import pekko.actor.typed.scaladsl.Behaviors
import scala.annotation.nowarn

import scala.concurrent.duration._

object SupervisionCompileOnly {

  val behavior = Behaviors.empty[String]

  // #restart
  Behaviors.supervise(behavior).onFailure[IllegalStateException](SupervisorStrategy.restart)
  // #restart

  // #resume
  Behaviors.supervise(behavior).onFailure[IllegalStateException](SupervisorStrategy.resume)
  // #resume

  // #restart-limit
  Behaviors
    .supervise(behavior)
    .onFailure[IllegalStateException](
      SupervisorStrategy.restart.withLimit(maxNrOfRetries = 10, withinTimeRange = 10.seconds))
  // #restart-limit

  // #multiple
  Behaviors
    .supervise(Behaviors.supervise(behavior).onFailure[IllegalStateException](SupervisorStrategy.restart))
    .onFailure[IllegalArgumentException](SupervisorStrategy.stop)
  // #multiple

  // #wrap
  object Counter {
    sealed trait Command
    case class Increment(nr: Int) extends Command
    case class GetCount(replyTo: ActorRef[Int]) extends Command

    // #top-level
    def apply(): Behavior[Command] =
      Behaviors.supervise(counter(1)).onFailure(SupervisorStrategy.restart)
    // #top-level

    private def counter(count: Int): Behavior[Command] =
      Behaviors.receiveMessage[Command] {
        case Increment(nr: Int) =>
          counter(count + nr)
        case GetCount(replyTo) =>
          replyTo ! count
          Behaviors.same
      }
  }
  // #wrap

  // #restart-stop-children
  def child(size: Long): Behavior[String] =
    Behaviors.receiveMessage(msg => child(size + msg.length))

  def parent: Behavior[String] = {
    Behaviors
      .supervise[String] {
        Behaviors.setup { ctx =>
          val child1 = ctx.spawn(child(0), "child1")
          val child2 = ctx.spawn(child(0), "child2")

          Behaviors.receiveMessage[String] { msg =>
            // message handling that might throw an exception
            val parts = msg.split(" ")
            child1 ! parts(0)
            child2 ! parts(1)
            Behaviors.same
          }
        }
      }
      .onFailure(SupervisorStrategy.restart)
  }
  // #restart-stop-children

  // #restart-keep-children
  def parent2: Behavior[String] = {
    Behaviors.setup { ctx =>
      val child1 = ctx.spawn(child(0), "child1")
      val child2 = ctx.spawn(child(0), "child2")

      // supervision strategy inside the setup to not recreate children on restart
      Behaviors
        .supervise {
          Behaviors.receiveMessage[String] { msg =>
            // message handling that might throw an exception
            val parts = msg.split(" ")
            child1 ! parts(0)
            child2 ! parts(1)
            Behaviors.same
          }
        }
        .onFailure(SupervisorStrategy.restart.withStopChildren(false))
    }
  }
  // #restart-keep-children

  trait Resource {
    def close(): Unit
    def process(parts: Array[String]): Unit
  }
  def claimResource(): Resource = ???

  @nowarn("msg=never used")
  // #restart-PreRestart-signal
  def withPreRestart: Behavior[String] = {
    Behaviors
      .supervise[String] {
        Behaviors.setup { ctx =>
          val resource = claimResource()

          Behaviors
            .receiveMessage[String] { msg =>
              // message handling that might throw an exception

              val parts = msg.split(" ")
              resource.process(parts)
              Behaviors.same
            }
            .receiveSignal {
              case (_, signal) if signal == PreRestart || signal == PostStop =>
                resource.close()
                Behaviors.same
            }
        }
      }
      .onFailure[Exception](SupervisorStrategy.restart)
  }

  // #restart-PreRestart-signal
}
