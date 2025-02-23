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

package jdocs.coordination;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.coordination.lease.LeaseSettings;
import org.apache.pekko.coordination.lease.javadsl.Lease;
import org.apache.pekko.coordination.lease.javadsl.LeaseProvider;
import org.apache.pekko.testkit.javadsl.TestKit;
import docs.coordination.LeaseDocSpec;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class LeaseDocTest extends JUnitSuite {
  // #lease-example
  static class SampleLease extends Lease {

    private LeaseSettings settings;

    public SampleLease(LeaseSettings settings) {
      this.settings = settings;
    }

    @Override
    public LeaseSettings getSettings() {
      return settings;
    }

    @Override
    public CompletionStage<Boolean> acquire() {
      return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletionStage<Boolean> acquire(Consumer<Optional<Throwable>> leaseLostCallback) {
      return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletionStage<Boolean> release() {
      return CompletableFuture.completedFuture(true);
    }

    @Override
    public boolean checkLease() {
      return true;
    }
  }
  // #lease-example

  private static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("LeaseDocTest", LeaseDocSpec.config());
  }

  @AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  private void doSomethingImportant(Optional<Throwable> leaseLostReason) {}

  @Test
  public void javaLeaseBeLoadableFromJava() {
    // #lease-usage
    Lease lease =
        LeaseProvider.get(system).getLease("<name of the lease>", "jdocs-lease", "<owner name>");
    CompletionStage<Boolean> acquired = lease.acquire();
    boolean stillAcquired = lease.checkLease();
    CompletionStage<Boolean> released = lease.release();
    // #lease-usage

    // #lost-callback
    lease.acquire(this::doSomethingImportant);
    // #lost-callback

    // #cluster-owner
    // String owner = Cluster.get(system).selfAddress().hostPort();
    // #cluster-owner

  }

  @Test
  public void scalaLeaseBeLoadableFromJava() {
    Lease lease =
        LeaseProvider.get(system).getLease("<name of the lease>", "docs-lease", "<owner name>");
    CompletionStage<Boolean> acquired = lease.acquire();
    boolean stillAcquired = lease.checkLease();
    CompletionStage<Boolean> released = lease.release();
    lease.acquire(this::doSomethingImportant);
  }
}
