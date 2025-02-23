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

package typed.tutorial_5

import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import typed.tutorial_5.DeviceManager._

class DeviceManagerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "DeviceManager actor" must {

    "reply to registration requests" in {
      val probe = createTestProbe[DeviceRegistered]()
      val managerActor = spawn(DeviceManager())

      managerActor ! RequestTrackDevice("group1", "device", probe.ref)
      val registered1 = probe.receiveMessage()

      // another group
      managerActor ! RequestTrackDevice("group2", "device", probe.ref)
      val registered2 = probe.receiveMessage()

      registered1.device should !==(registered2.device)
    }

  }

}
