/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko.util.ByteIterator.ByteArrayIterator

class ByteIteratorSpec extends AnyWordSpec with Matchers {
  "A ByteIterator" should {

    "correctly implement indexOf" in {
      // Since the 'indexOf' operator invalidates the iterator,
      // we must create a new one for each test:
      def freshIterator(): ByteIterator = ByteArrayIterator(Array(0x20, 0x20, 0x10, 0x20, 0x20, 0x10))
      freshIterator().indexOf(0x20) should be(0)
      freshIterator().indexOf(0x10) should be(2)

      freshIterator().indexOf(0x20, 1) should be(1)
      freshIterator().indexOf(0x10, 1) should be(2)
      freshIterator().indexOf(0x10, 3) should be(5)

      // There is also an indexOf with another signature, which is hard to invoke :D
      def otherIndexOf(iterator: ByteIterator, byte: Byte, from: Int): Int =
        classOf[ByteIterator]
          .getMethod("indexOf", classOf[Byte], classOf[Int])
          .invoke(iterator, byte.asInstanceOf[Object], from.asInstanceOf[Object])
          .asInstanceOf[Int]

      otherIndexOf(freshIterator(), 0x20, 1) should be(1)
      otherIndexOf(freshIterator(), 0x10, 1) should be(2)
      otherIndexOf(freshIterator(), 0x10, 3) should be(5)
    }
  }
}
