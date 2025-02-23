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

package org.apache.pekko.serialization.jackson

import com.fasterxml.jackson.databind.JsonNode

import org.apache.pekko.util.unused

/**
 * Data migration of old formats to current format can
 * be implemented in a concrete subclass and configured to
 * be used by the `JacksonSerializer` for a changed class.
 *
 * It is used when deserializing data of older version than the
 * [[JacksonMigration#currentVersion]]. You implement the transformation of the
 * JSON structure in the [[JacksonMigration#transform]] method. If you have changed the
 * class name you should override [[JacksonMigration#transformClassName]] and return
 * current class name.
 */
abstract class JacksonMigration {

  /**
   * Define current version, that is, the value used when serializing new data. The first version, when no
   * migration was used, is always 1.
   */
  def currentVersion: Int

  /**
   * Define the supported forward version this migration can read (must be greater or equal than `currentVersion`).
   * If this value is different from [[currentVersion]] a [[JacksonMigration]] may be required to downcast
   * the received payload to the current schema.
   */
  def supportedForwardVersion: Int = currentVersion

  require(
    currentVersion <= supportedForwardVersion,
    s"""The "currentVersion" [$currentVersion] of a JacksonMigration must be less or equal to the "supportedForwardVersion" [$supportedForwardVersion].""")

  /**
   * Override this method if you have changed the class name. Return
   * current class name.
   */
  def transformClassName(@unused fromVersion: Int, className: String): String =
    className

  /**
   * Implement the transformation of the old JSON structure to the new
   * JSON structure. The `JsonNode` is mutable so you can add and remove fields,
   * or change values. Note that you have to cast to specific sub-classes such
   * as `ObjectNode` and `ArrayNode` to get access to mutators.
   *
   * @param fromVersion the version of the old data
   * @param json the old JSON data
   */
  def transform(fromVersion: Int, json: JsonNode): JsonNode
}
