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

package org.apache.pekko.cluster

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.cluster.ClusterEvent.InitialStateAsEvents
import pekko.cluster.ClusterEvent.MemberUp
import pekko.remote.testkit.MultiNodeConfig
import pekko.testkit._

// reproducer for issue #29280
object MultiDcJoinMultiJvmSpec extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")
  val fourth = role("fourth")
  val fifth = role("fifth")

  nodeConfig(first, second, third)(ConfigFactory.parseString("""
    pekko {
      cluster.multi-data-center.self-data-center = alpha
    }
    """))

  nodeConfig(fourth, fifth)(ConfigFactory.parseString("""
    pekko {
      cluster.multi-data-center.self-data-center = beta
    }
    """))

  commonConfig(ConfigFactory.parseString("""
    pekko {
      actor.provider = cluster

      loggers = ["org.apache.pekko.testkit.TestEventListener"]
      loglevel = INFO

      remote.log-remote-lifecycle-events = off

      cluster {
        debug.verbose-heartbeat-logging = off
        debug.verbose-gossip-logging = off

        multi-data-center {
          cross-data-center-connections = 1
        }
      }
    }
    """))

}

class MultiDcJoinMultiJvmNode1 extends MultiDcJoinSpec
class MultiDcJoinMultiJvmNode2 extends MultiDcJoinSpec
class MultiDcJoinMultiJvmNode3 extends MultiDcJoinSpec
class MultiDcJoinMultiJvmNode4 extends MultiDcJoinSpec
class MultiDcJoinMultiJvmNode5 extends MultiDcJoinSpec

abstract class MultiDcJoinSpec extends MultiNodeClusterSpec(MultiDcJoinMultiJvmSpec) {
  import MultiDcJoinMultiJvmSpec._

  "Joining a multi-dc cluster" must {
    "make sure oldest is selected correctly" taggedAs LongRunningTest in {
      val fourthAddress = address(fourth)
      val fifthAddress = address(fifth)
      val sortedBetaAddresses = List(fourthAddress, fifthAddress).sorted
      // beta1 has lower address than beta2 and will therefore be chosen as leader
      // The problematic scenario in issue #29280 is triggered by beta2 joining first, moving itself to Up,
      // and then beta1 joining and also moving itself to Up.
      val (beta1, beta1Address, beta2, beta2Address) =
        if (sortedBetaAddresses.head == fourthAddress) (fourth, fourthAddress, fifth, fifthAddress)
        else (fifth, fifthAddress, fourth, fourthAddress)

      val observer = TestProbe("beta-observer")
      runOn(fourth, fifth) {
        Cluster(system).subscribe(observer.ref, InitialStateAsEvents, classOf[MemberUp])
      }

      // all alpha nodes
      awaitClusterUp(first, second, third)

      runOn(beta2) {
        Cluster(system).join(first)
        within(20.seconds) {
          awaitAssert {
            Cluster(system).state.members
              .exists(m => m.address == beta2Address && m.status == MemberStatus.Up) should ===(true)
          }
        }
      }
      enterBarrier("beta2-joined")

      runOn(beta1) {
        Cluster(system).join(second)
        within(10.seconds) {
          awaitAssert {
            Cluster(system).state.members
              .exists(m => m.address == beta1Address && m.status == MemberStatus.Up) should ===(true)
          }
        }
      }
      enterBarrier("beta1-joined")

      runOn(fourth, fifth) {
        val events = observer
          .receiveN(5)
          .map(_.asInstanceOf[MemberUp])
          .filter(_.member.dataCenter == "beta")
          .sortBy(_.member.upNumber)
        events.head.member.address should ===(beta2Address)
        events.head.member.upNumber should ===(1)
        events(1).member.address should ===(beta1Address)
        events(1).member.upNumber should ===(2)

        observer.expectNoMessage(2.seconds)
      }

      enterBarrier("done")
    }

  }

}
