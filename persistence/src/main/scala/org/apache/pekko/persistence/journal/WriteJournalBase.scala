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

package org.apache.pekko.persistence.journal

import scala.collection.immutable

import org.apache.pekko
import pekko.actor.Actor
import pekko.persistence.{ Persistence, PersistentEnvelope, PersistentRepr }
import pekko.persistence.AtomicWrite

private[pekko] trait WriteJournalBase {
  this: Actor =>

  val persistence = Persistence(context.system)
  private val eventAdapters = persistence.adaptersFor(self)

  protected def preparePersistentBatch(rb: immutable.Seq[PersistentEnvelope]): immutable.Seq[AtomicWrite] =
    rb.collect { // collect instead of flatMap to avoid Some allocations
      case a: AtomicWrite =>
        // don't store sender
        a.copy(payload = a.payload.map(p => adaptToJournal(p.update(sender = Actor.noSender))))
    }

  /** INTERNAL API */
  private[pekko] final def adaptFromJournal(repr: PersistentRepr): immutable.Seq[PersistentRepr] =
    eventAdapters.get(repr.payload.getClass).fromJournal(repr.payload, repr.manifest).events.map { adaptedPayload =>
      repr.withPayload(adaptedPayload)
    }

  /** INTERNAL API */
  private[pekko] final def adaptToJournal(repr: PersistentRepr): PersistentRepr = {
    val payload = repr.payload
    val adapter = eventAdapters.get(payload.getClass)

    // IdentityEventAdapter returns "" as manifest and normally the incoming PersistentRepr
    // doesn't have an assigned manifest, but when WriteMessages is sent directly to the
    // journal for testing purposes we want to preserve the original manifest instead of
    // letting IdentityEventAdapter clearing it out.
    if (adapter == IdentityEventAdapter || adapter.isInstanceOf[NoopWriteEventAdapter])
      repr
    else {
      repr.withPayload(adapter.toJournal(payload)).withManifest(adapter.manifest(payload))
    }
  }

}
