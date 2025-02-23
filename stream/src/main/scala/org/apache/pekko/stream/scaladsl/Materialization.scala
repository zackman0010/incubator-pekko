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

package org.apache.pekko.stream.scaladsl

import org.apache.pekko.NotUsed

/**
 * Convenience functions for often-encountered purposes like keeping only the
 * left (first) or only the right (second) of two input values.
 */
object Keep {
  private val _left = (l: Any, _: Any) => l
  private val _right = (_: Any, r: Any) => r
  private val _both = (l: Any, r: Any) => (l, r)
  private val _none = (_: Any, _: Any) => NotUsed

  def left[L, R]: (L, R) => L = _left.asInstanceOf[(L, R) => L]
  def right[L, R]: (L, R) => R = _right.asInstanceOf[(L, R) => R]
  def both[L, R]: (L, R) => (L, R) = _both.asInstanceOf[(L, R) => (L, R)]
  def none[L, R]: (L, R) => NotUsed = _none.asInstanceOf[(L, R) => NotUsed]
}
