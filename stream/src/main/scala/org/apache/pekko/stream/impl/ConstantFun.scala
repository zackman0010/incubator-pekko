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

package org.apache.pekko.stream.impl

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.japi.{ Pair => JPair }
import pekko.japi.function.{ Function => JFun, Function2 => JFun2 }

/**
 * INTERNAL API
 */
@deprecated("Use org.apache.pekko.util.ConstantFun instead", "Akka 2.5.0")
@InternalApi private[pekko] object ConstantFun {
  private[this] val JavaIdentityFunction = new JFun[Any, Any] {
    @throws(classOf[Exception]) override def apply(param: Any): Any = param
  }

  val JavaPairFunction = new JFun2[AnyRef, AnyRef, AnyRef JPair AnyRef] {
    def apply(p1: AnyRef, p2: AnyRef): AnyRef JPair AnyRef = JPair(p1, p2)
  }

  def javaCreatePairFunction[A, B]: JFun2[A, B, JPair[A, B]] = JavaPairFunction.asInstanceOf[JFun2[A, B, JPair[A, B]]]

  def javaIdentityFunction[T]: JFun[T, T] = JavaIdentityFunction.asInstanceOf[JFun[T, T]]

  def scalaIdentityFunction[T]: T => T = conforms.asInstanceOf[Function[T, T]]

  def scalaAnyToNone[A, B]: A => Option[B] = none
  def scalaAnyTwoToNone[A, B, C]: (A, B) => Option[C] = two2none
  def javaAnyToNone[A, B]: A => Option[B] = none

  val conforms = (a: Any) => a

  val zeroLong = (_: Any) => 0L

  val oneLong = (_: Any) => 1L

  val oneInt = (_: Any) => 1

  val none = (_: Any) => None

  val two2none = (_: Any, _: Any) => None

}
