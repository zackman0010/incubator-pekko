/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.scaladsl
import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.LoggingTestKit
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.RecoveryCompleted
import pekko.persistence.typed.SnapshotCompleted
import pekko.persistence.typed.SnapshotFailed
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.event.Level

import java.util.concurrent.atomic.AtomicInteger

// Note that the spec name here is important since there are heuristics in place to avoid names
// starting with EventSourcedBehavior
class LoggerSourceSpec
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorSpec.conf)
    with AnyWordSpecLike
    with LogCapturing {

  private val pidCounter = new AtomicInteger(0)
  private def nextPid(): PersistenceId = PersistenceId.ofUniqueId(s"c${pidCounter.incrementAndGet()})")

  def behavior: Behavior[String] = Behaviors.setup { ctx =>
    ctx.log.info("setting-up-behavior")
    EventSourcedBehavior[String, String, String](nextPid(), emptyState = "",
      commandHandler = (_, _) => {
        ctx.log.info("command-received")
        Effect.persist("evt")
      },
      eventHandler = (state, _) => {
        ctx.log.info("event-received")
        state
      }).receiveSignal {
      case (_, RecoveryCompleted)    => ctx.log.info("recovery-completed")
      case (_, SnapshotCompleted(_)) =>
      case (_, SnapshotFailed(_, _)) =>
    }
  }

  "log from context" should {

    // note that these are somewhat intermingled to make sure no log event from
    // one test case leaks to another, the actual log class is what is tested in each individual case

    "log from setup" in {
      LoggingTestKit.info("recovery-completed").expect {
        eventFilterFor("setting-up-behavior").expect {
          spawn(behavior)
        }
      }

    }

    "log from recovery completed" in {
      LoggingTestKit.info("setting-up-behavior").expect {
        eventFilterFor("recovery-completed").expect {
          spawn(behavior)
        }
      }
    }

    "log from command handler" in {
      LoggingTestKit.empty
        .withLogLevel(Level.INFO)
        .withMessageRegex("(setting-up-behavior|recovery-completed|event-received)")
        .withOccurrences(3)
        .expect {
          eventFilterFor("command-received").expect {
            spawn(behavior) ! "cmd"
          }
        }
    }

    "log from event handler" in {
      LoggingTestKit.empty
        .withLogLevel(Level.INFO)
        .withMessageRegex("(setting-up-behavior|recovery-completed|command-received)")
        .withOccurrences(3)
        .expect {
          eventFilterFor("event-received").expect {
            spawn(behavior) ! "cmd"
          }
        }
    }

    "use the user provided name" in {

      val behavior: Behavior[String] = Behaviors.setup[String] { ctx =>
        ctx.setLoggerName("my-custom-name")
        EventSourcedBehavior[String, String, String](nextPid(), emptyState = "",
          commandHandler = (_, _) => {
            ctx.log.info("command-received")
            Effect.persist("evt")
          }, eventHandler = (state, _) => state)
      }

      val actor = spawn(behavior)

      LoggingTestKit.info("command-received").withLoggerName("my-custom-name").withOccurrences(1).expect {
        actor ! "cmd"
      }
    }
  }

  def eventFilterFor(logMsg: String) =
    LoggingTestKit.custom { logEvent =>
      logEvent.message == logMsg && logEvent.loggerName == classOf[LoggerSourceSpec].getName
    }

}
