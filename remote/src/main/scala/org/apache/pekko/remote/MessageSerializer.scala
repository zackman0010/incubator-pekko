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

package org.apache.pekko.remote

import scala.util.control.NonFatal
import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.annotation.InternalApi
import pekko.protobufv3.internal.ByteString
import pekko.remote.WireFormats._
import pekko.remote.artery.{ EnvelopeBuffer, HeaderBuilder, OutboundEnvelope }
import pekko.serialization._
import pekko.util.unused

/**
 * INTERNAL API
 *
 * MessageSerializer is a helper for serializing and deserialize messages
 */
@InternalApi
private[pekko] object MessageSerializer {

  class SerializationException(msg: String, cause: Throwable) extends RuntimeException(msg, cause)

  /**
   * Uses Pekko Serialization for the specified ActorSystem to transform the given MessageProtocol to a message
   */
  def deserialize(system: ExtendedActorSystem, messageProtocol: SerializedMessage): AnyRef = {
    SerializationExtension(system)
      .deserialize(
        messageProtocol.getMessage.toByteArray,
        messageProtocol.getSerializerId,
        if (messageProtocol.hasMessageManifest) messageProtocol.getMessageManifest.toStringUtf8 else "")
      .get
  }

  /**
   * Uses Pekko Serialization for the specified ActorSystem to transform the given message to a MessageProtocol
   * Throws `NotSerializableException` if serializer was not configured for the message type.
   * Throws `MessageSerializer.SerializationException` if exception was thrown from `toBinary` of the
   * serializer.
   */
  def serialize(system: ExtendedActorSystem, message: AnyRef): SerializedMessage = {
    val s = SerializationExtension(system)
    val serializer = s.findSerializerFor(message)
    val builder = SerializedMessage.newBuilder

    val oldInfo = Serialization.currentTransportInformation.value
    try {
      if (oldInfo eq null)
        Serialization.currentTransportInformation.value = system.provider.serializationInformation

      builder.setMessage(ByteStringUtils.toProtoByteStringUnsafe(serializer.toBinary(message)))
      builder.setSerializerId(serializer.identifier)

      val ms = Serializers.manifestFor(serializer, message)
      if (ms.nonEmpty) builder.setMessageManifest(ByteString.copyFromUtf8(ms))

      builder.build
    } catch {
      case NonFatal(e) =>
        throw new SerializationException(
          s"Failed to serialize remote message [${message.getClass}] " +
          s"using serializer [${serializer.getClass}].",
          e)
    } finally Serialization.currentTransportInformation.value = oldInfo
  }

  def serializeForArtery(
      serialization: Serialization,
      outboundEnvelope: OutboundEnvelope,
      headerBuilder: HeaderBuilder,
      envelope: EnvelopeBuffer): Unit = {
    val message = outboundEnvelope.message
    val serializer = serialization.findSerializerFor(message)
    val oldInfo = Serialization.currentTransportInformation.value
    try {
      if (oldInfo eq null)
        Serialization.currentTransportInformation.value = serialization.serializationInformation

      headerBuilder.setSerializer(serializer.identifier)
      headerBuilder.setManifest(Serializers.manifestFor(serializer, message))
      envelope.writeHeader(headerBuilder, outboundEnvelope)

      serializer match {
        case ser: ByteBufferSerializer => ser.toBinary(message, envelope.byteBuffer)
        case _                         => envelope.byteBuffer.put(serializer.toBinary(message))
      }

    } finally Serialization.currentTransportInformation.value = oldInfo
  }

  def deserializeForArtery(
      @unused system: ExtendedActorSystem,
      @unused originUid: Long,
      serialization: Serialization,
      serializer: Int,
      classManifest: String,
      envelope: EnvelopeBuffer): AnyRef = {
    serialization.deserializeByteBuffer(envelope.byteBuffer, serializer, classManifest)
  }
}
