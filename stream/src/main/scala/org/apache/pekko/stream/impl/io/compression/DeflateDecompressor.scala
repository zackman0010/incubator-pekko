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

package org.apache.pekko.stream.impl.io.compression

import java.util.zip.Inflater

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.Attributes

/** INTERNAL API */
@InternalApi private[pekko] class DeflateDecompressor(maxBytesPerChunk: Int, nowrap: Boolean)
    extends DeflateDecompressorBase(maxBytesPerChunk) {

  def this(maxBytesPerChunk: Int) = this(maxBytesPerChunk, false) // for binary compatibility

  override def createLogic(attr: Attributes) = new DecompressorParsingLogic {
    override val inflater: Inflater = new Inflater(nowrap)

    override case object inflating extends Inflate(noPostProcessing = true) {
      override def onTruncation(): Unit = completeStage()
    }

    override def afterInflate = inflating
    override def afterBytesRead(buffer: Array[Byte], offset: Int, length: Int): Unit = {}

    startWith(inflating)
  }
}
