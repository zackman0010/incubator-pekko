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

package org.apache.pekko.persistence.query.scaladsl

import org.apache.pekko
import pekko.NotUsed
import pekko.persistence.query.{ EventEnvelope, Offset }
import pekko.stream.scaladsl.Source

/**
 * A plugin may optionally support this query by implementing this trait.
 */
trait EventsByTagQuery extends ReadJournal {

  /**
   * Query events that have a specific tag. A tag can for example correspond to an
   * aggregate root type (in DDD terminology).
   *
   * The consumer can keep track of its current position in the event stream by storing the
   * `offset` and restart the query from a given `offset` after a crash/restart.
   *
   * The exact meaning of the `offset` depends on the journal and must be documented by the
   * read journal plugin. It may be a sequential id number that uniquely identifies the
   * position of each event within the event stream. Distributed data stores cannot easily
   * support those semantics and they may use a weaker meaning. For example it may be a
   * timestamp (taken when the event was created or stored). Timestamps are not unique and
   * not strictly ordered, since clocks on different machines may not be synchronized.
   *
   * In strongly consistent stores, where the `offset` is unique and strictly ordered, the
   * stream should start from the next event after the `offset`. Otherwise, the read journal
   * should ensure that between an invocation that returned an event with the given
   * `offset`, and this invocation, no events are missed. Depending on the journal
   * implementation, this may mean that this invocation will return events that were already
   * returned by the previous invocation, including the event with the passed in `offset`.
   *
   * The returned event stream should be ordered by `offset` if possible, but this can also be
   * difficult to fulfill for a distributed data store. The order must be documented by the
   * read journal plugin.
   *
   * The stream is not completed when it reaches the end of the currently stored events,
   * but it continues to push new events when new events are persisted.
   * Corresponding query that is completed when it reaches the end of the currently
   * stored events is provided by [[CurrentEventsByTagQuery#currentEventsByTag]].
   */
  def eventsByTag(tag: String, offset: Offset): Source[EventEnvelope, NotUsed]

}
