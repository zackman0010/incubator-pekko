/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.TestSink

class FlowMapErrorSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 1
    pekko.stream.materializer.max-input-buffer-size = 1
  """) {

  val ex = new RuntimeException("ex") with NoStackTrace
  val boom = new Exception("BOOM!") with NoStackTrace

  "A MapError" must {
    "mapError when there is a handler" in {
      Source(1 to 4)
        .map { a =>
          if (a == 3) throw ex else a
        }
        .mapError { case _: Throwable => boom }
        .runWith(TestSink.probe[Int])
        .request(3)
        .expectNext(1)
        .expectNext(2)
        .expectError(boom)
    }

    "fail the stream with exception thrown in handler (and log it)" in {
      Source(1 to 3)
        .map { a =>
          if (a == 2) throw ex else a
        }
        .mapError { case _: Exception => throw boom }
        .runWith(TestSink.probe[Int])
        .requestNext(1)
        .request(1)
        .expectError(boom)
    }

    "pass through the original exception if partial function does not handle it" in {
      Source(1 to 3)
        .map { a =>
          if (a == 2) throw ex else a
        }
        .mapError { case _: IndexOutOfBoundsException => boom }
        .runWith(TestSink.probe[Int])
        .requestNext(1)
        .request(1)
        .expectError(ex)
    }

    "not influence stream when there is no exceptions" in {
      Source(1 to 3)
        .map(identity)
        .mapError { case _: Throwable => boom }
        .runWith(TestSink.probe[Int])
        .request(3)
        .expectNextN(1 to 3)
        .expectComplete()
    }

    "finish stream if it's empty" in {
      Source.empty.mapError { case _: Throwable => boom }.runWith(TestSink.probe[Int]).request(1).expectComplete()
    }
  }
}
