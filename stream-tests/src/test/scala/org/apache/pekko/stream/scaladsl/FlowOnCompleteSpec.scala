/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.Done
import pekko.stream.Materializer
import pekko.stream.testkit._
import pekko.testkit.TestProbe

class FlowOnCompleteSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) with ScriptedTest {

  "A Flow with onComplete" must {

    "invoke callback on normal completion" in {
      val onCompleteProbe = TestProbe()
      val p = TestPublisher.manualProbe[Int]()
      Source.fromPublisher(p).to(Sink.onComplete[Int](onCompleteProbe.ref ! _)).run()
      val proc = p.expectSubscription()
      proc.expectRequest()
      proc.sendNext(42)
      onCompleteProbe.expectNoMessage(100.millis)
      proc.sendComplete()
      onCompleteProbe.expectMsg(Success(Done))
    }

    "yield the first error" in {
      val onCompleteProbe = TestProbe()
      val p = TestPublisher.manualProbe[Int]()
      Source.fromPublisher(p).to(Sink.onComplete[Int](onCompleteProbe.ref ! _)).run()
      val proc = p.expectSubscription()
      proc.expectRequest()
      val ex = new RuntimeException("ex") with NoStackTrace
      proc.sendError(ex)
      onCompleteProbe.expectMsg(Failure(ex))
      onCompleteProbe.expectNoMessage(100.millis)
    }

    "invoke callback for an empty stream" in {
      val onCompleteProbe = TestProbe()
      val p = TestPublisher.manualProbe[Int]()
      Source.fromPublisher(p).to(Sink.onComplete[Int](onCompleteProbe.ref ! _)).run()
      val proc = p.expectSubscription()
      proc.expectRequest()
      proc.sendComplete()
      onCompleteProbe.expectMsg(Success(Done))
      onCompleteProbe.expectNoMessage(100.millis)
    }

    "invoke callback after transform and foreach steps " in {
      val onCompleteProbe = TestProbe()
      val p = TestPublisher.manualProbe[Int]()
      import system.dispatcher // for the Future.onComplete
      val foreachSink = Sink.foreach[Int] { x =>
        onCompleteProbe.ref ! ("foreach-" + x)
      }
      val future = Source
        .fromPublisher(p)
        .map { x =>
          onCompleteProbe.ref ! ("map-" + x)
          x
        }
        .runWith(foreachSink)
      future.onComplete { onCompleteProbe.ref ! _ }
      val proc = p.expectSubscription()
      proc.expectRequest()
      proc.sendNext(42)
      proc.sendComplete()
      onCompleteProbe.expectMsg("map-42")
      onCompleteProbe.expectMsg("foreach-42")
      onCompleteProbe.expectMsg(Success(Done))
    }

    "yield error on abrupt termination" in {
      val mat = Materializer(system)
      val onCompleteProbe = TestProbe()
      val p = TestPublisher.manualProbe[Int]()
      Source.fromPublisher(p).to(Sink.onComplete[Int](onCompleteProbe.ref ! _)).run()(mat)
      val proc = p.expectSubscription()
      proc.expectRequest()
      mat.shutdown()

      onCompleteProbe.expectMsgType[Failure[_]]
    }

  }

}
