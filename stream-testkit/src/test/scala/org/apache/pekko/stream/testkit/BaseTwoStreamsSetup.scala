/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.testkit

import scala.collection.immutable
import scala.util.control.NoStackTrace

import org.reactivestreams.Publisher

import org.apache.pekko
import pekko.stream.scaladsl._
import pekko.stream.testkit.scaladsl.StreamTestKit._
import pekko.testkit.PekkoSpec

abstract class BaseTwoStreamsSetup extends PekkoSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
    pekko.stream.materializer.max-input-buffer-size = 2
  """) {

  val TestException = new RuntimeException("test") with NoStackTrace

  type Outputs

  def setup(p1: Publisher[Int], p2: Publisher[Int]): TestSubscriber.Probe[Outputs]

  def failedPublisher[T]: Publisher[T] = TestPublisher.error[T](TestException)

  def completedPublisher[T]: Publisher[T] = TestPublisher.empty[T]()

  def nonemptyPublisher[T](elems: immutable.Iterable[T]): Publisher[T] = Source(elems).runWith(Sink.asPublisher(false))

  def soonToFailPublisher[T]: Publisher[T] = TestPublisher.lazyError[T](TestException)

  def soonToCompletePublisher[T]: Publisher[T] = TestPublisher.lazyEmpty[T]

  def commonTests() = {
    "work with two immediately completed publishers" in assertAllStagesStopped {
      val subscriber = setup(completedPublisher, completedPublisher)
      subscriber.expectSubscriptionAndComplete()
    }

    "work with two delayed completed publishers" in assertAllStagesStopped {
      val subscriber = setup(soonToCompletePublisher, soonToCompletePublisher)
      subscriber.expectSubscriptionAndComplete()
    }

    "work with one immediately completed and one delayed completed publisher" in assertAllStagesStopped {
      val subscriber = setup(completedPublisher, soonToCompletePublisher)
      subscriber.expectSubscriptionAndComplete()
    }

    "work with two immediately failed publishers" in assertAllStagesStopped {
      val subscriber = setup(failedPublisher, failedPublisher)
      subscriber.expectSubscriptionAndError(TestException)
    }

    "work with two delayed failed publishers" in assertAllStagesStopped {
      val subscriber = setup(soonToFailPublisher, soonToFailPublisher)
      subscriber.expectSubscriptionAndError(TestException)
    }

    // Warning: The two test cases below are somewhat implementation specific and might fail if the implementation
    // is changed. They are here to be an early warning though.
    "work with one immediately failed and one delayed failed publisher (case 1)" in assertAllStagesStopped {
      val subscriber = setup(soonToFailPublisher, failedPublisher)
      subscriber.expectSubscriptionAndError(TestException)
    }

    "work with one immediately failed and one delayed failed publisher (case 2)" in assertAllStagesStopped {
      val subscriber = setup(failedPublisher, soonToFailPublisher)
      subscriber.expectSubscriptionAndError(TestException)
    }
  }

}
