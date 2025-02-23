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

package org.apache.pekko.cluster

import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.ConfigurationException
import pekko.actor.{ ActorSystem, ExtendedActorSystem, Props }

/**
 * INTERNAL API
 */
private[cluster] object DowningProvider {

  /**
   * @param fqcn Fully qualified class name of the implementation to be loaded.
   * @param system Actor system used to load the implemntation
   * @return the provider or throws a [[pekko.ConfigurationException]] if loading it fails
   */
  def load(fqcn: String, system: ActorSystem): DowningProvider = {
    val eas = system.asInstanceOf[ExtendedActorSystem]
    eas.dynamicAccess
      .createInstanceFor[DowningProvider](fqcn, List((classOf[ActorSystem], system)))
      .recover {
        case e => throw new ConfigurationException(s"Could not create cluster downing provider [$fqcn]", e)
      }
      .get
  }

}

/**
 * API for plugins that will handle downing of cluster nodes. Concrete plugins must subclass and
 * have a public one argument constructor accepting an [[pekko.actor.ActorSystem]].
 *
 * A custom `DowningProvider` can be configured with `pekko.cluster.downing-provider-class`
 *
 * When implementing a downing provider you should make sure that it will not split the cluster into
 * several separate clusters in case of network problems or system overload (long GC pauses). This
 * is much more difficult than it might be perceived at first, so carefully read the concerns and scenarios
 * described in https://pekko.apache.org/docs/pekko/current/split-brain-resolver.html
 */
abstract class DowningProvider {

  /**
   * Time margin after which shards or singletons that belonged to a downed/removed
   * partition are created in surviving partition. The purpose of this margin is that
   * in case of a network partition the persistent actors in the non-surviving partitions
   * must be stopped before corresponding persistent actors are started somewhere else.
   * This is useful if you implement downing strategies that handle network partitions,
   * e.g. by keeping the larger side of the partition and shutting down the smaller side.
   */
  def downRemovalMargin: FiniteDuration

  /**
   * If a props is returned it is created as a child of the core cluster daemon on cluster startup.
   * It should then handle downing using the regular [[pekko.cluster.Cluster]] APIs.
   * The actor will run on the same dispatcher as the cluster actor if dispatcher not configured.
   *
   * May throw an exception which will then immediately lead to Cluster stopping, as the downing
   * provider is vital to a working cluster.
   */
  def downingActorProps: Option[Props]

}

/**
 * Default downing provider used when no provider is configured.
 */
final class NoDowning(system: ActorSystem) extends DowningProvider {
  override def downRemovalMargin: FiniteDuration = Cluster(system).settings.DownRemovalMargin
  override val downingActorProps: Option[Props] = None
}
