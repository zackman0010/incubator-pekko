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

package jdocs.routing;

import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;

import jdocs.AbstractJavaTest;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

import com.typesafe.config.ConfigFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.time.Duration;

import org.apache.pekko.actor.ActorSystem;

// #imports1
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Terminated;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.routing.ActorRefRoutee;
import org.apache.pekko.routing.Routee;
import org.apache.pekko.routing.Router;

// #imports1

// #imports2
import org.apache.pekko.actor.Address;
import org.apache.pekko.actor.AddressFromURIString;
import org.apache.pekko.actor.Kill;
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.SupervisorStrategy;
import org.apache.pekko.actor.OneForOneStrategy;
import org.apache.pekko.remote.routing.RemoteRouterConfig;
import org.apache.pekko.routing.Broadcast;
import org.apache.pekko.routing.BroadcastGroup;
import org.apache.pekko.routing.BroadcastPool;
import org.apache.pekko.routing.ConsistentHashingGroup;
import org.apache.pekko.routing.ConsistentHashingPool;
import org.apache.pekko.routing.DefaultResizer;
import org.apache.pekko.routing.FromConfig;
import org.apache.pekko.routing.RandomGroup;
import org.apache.pekko.routing.RandomPool;
import org.apache.pekko.routing.RoundRobinGroup;
import org.apache.pekko.routing.RoundRobinPool;
import org.apache.pekko.routing.RoundRobinRoutingLogic;
import org.apache.pekko.routing.ScatterGatherFirstCompletedGroup;
import org.apache.pekko.routing.ScatterGatherFirstCompletedPool;
import org.apache.pekko.routing.BalancingPool;
import org.apache.pekko.routing.SmallestMailboxPool;
import org.apache.pekko.routing.TailChoppingGroup;
import org.apache.pekko.routing.TailChoppingPool;

// #imports2

public class RouterDocTest extends AbstractJavaTest {

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource(
          "RouterDocTest", ConfigFactory.parseString(docs.routing.RouterDocSpec.config()));

  private final ActorSystem system = actorSystemResource.getSystem();

  public
  // #router-in-actor
  static final class Work implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String payload;

    public Work(String payload) {
      this.payload = payload;
    }
  }

  // #router-in-actor
  public
  // #router-in-actor
  static class Master extends AbstractActor {

    Router router;

    {
      List<Routee> routees = new ArrayList<Routee>();
      for (int i = 0; i < 5; i++) {
        ActorRef r = getContext().actorOf(Props.create(Worker.class));
        getContext().watch(r);
        routees.add(new ActorRefRoutee(r));
      }
      router = new Router(new RoundRobinRoutingLogic(), routees);
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              Work.class,
              message -> {
                router.route(message, getSender());
              })
          .match(
              Terminated.class,
              message -> {
                router = router.removeRoutee(message.actor());
                ActorRef r = getContext().actorOf(Props.create(Worker.class));
                getContext().watch(r);
                router = router.addRoutee(new ActorRefRoutee(r));
              })
          .build();
    }
  }

  // #router-in-actor

  public static class Worker extends AbstractActor {
    @Override
    public Receive createReceive() {
      return receiveBuilder().build();
    }
  }

  public static class Echo extends AbstractActor {
    @Override
    public Receive createReceive() {
      return receiveBuilder().matchAny(message -> getSender().tell(message, getSelf())).build();
    }
  }

  public static class Replier extends AbstractActor {
    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .matchAny(
              message -> {
                // #reply-with-self
                getSender().tell("reply", getSelf());
                // #reply-with-self

                // #reply-with-parent
                getSender().tell("reply", getContext().getParent());
                // #reply-with-parent
              })
          .build();
    }
  }

  public
  // #create-worker-actors
  static class Workers extends AbstractActor {
    @Override
    public void preStart() {
      getContext().actorOf(Props.create(Worker.class), "w1");
      getContext().actorOf(Props.create(Worker.class), "w2");
      getContext().actorOf(Props.create(Worker.class), "w3");
    }
    // ...
    // #create-worker-actors

    @Override
    public Receive createReceive() {
      return receiveBuilder().build();
    }
  }

  public static class Parent extends AbstractActor {

    // #paths
    List<String> paths = Arrays.asList("/user/workers/w1", "/user/workers/w2", "/user/workers/w3");
    // #paths

    // #round-robin-pool-1
    ActorRef router1 =
        getContext().actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router1");
    // #round-robin-pool-1

    // #round-robin-pool-2
    ActorRef router2 =
        getContext().actorOf(new RoundRobinPool(5).props(Props.create(Worker.class)), "router2");
    // #round-robin-pool-2

    // #round-robin-group-1
    ActorRef router3 = getContext().actorOf(FromConfig.getInstance().props(), "router3");
    // #round-robin-group-1

    // #round-robin-group-2
    ActorRef router4 = getContext().actorOf(new RoundRobinGroup(paths).props(), "router4");
    // #round-robin-group-2

    // #random-pool-1
    ActorRef router5 =
        getContext().actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router5");
    // #random-pool-1

    // #random-pool-2
    ActorRef router6 =
        getContext().actorOf(new RandomPool(5).props(Props.create(Worker.class)), "router6");
    // #random-pool-2

    // #random-group-1
    ActorRef router7 = getContext().actorOf(FromConfig.getInstance().props(), "router7");
    // #random-group-1

    // #random-group-2
    ActorRef router8 = getContext().actorOf(new RandomGroup(paths).props(), "router8");
    // #random-group-2

    // #balancing-pool-1
    ActorRef router9 =
        getContext().actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router9");
    // #balancing-pool-1

    // #balancing-pool-2
    ActorRef router10 =
        getContext().actorOf(new BalancingPool(5).props(Props.create(Worker.class)), "router10");
    // #balancing-pool-2

    // #smallest-mailbox-pool-1
    ActorRef router11 =
        getContext()
            .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router11");
    // #smallest-mailbox-pool-1

    // #smallest-mailbox-pool-2
    ActorRef router12 =
        getContext()
            .actorOf(new SmallestMailboxPool(5).props(Props.create(Worker.class)), "router12");
    // #smallest-mailbox-pool-2

    // #broadcast-pool-1
    ActorRef router13 =
        getContext()
            .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router13");
    // #broadcast-pool-1

    // #broadcast-pool-2
    ActorRef router14 =
        getContext().actorOf(new BroadcastPool(5).props(Props.create(Worker.class)), "router14");
    // #broadcast-pool-2

    // #broadcast-group-1
    ActorRef router15 = getContext().actorOf(FromConfig.getInstance().props(), "router15");
    // #broadcast-group-1

    // #broadcast-group-2
    ActorRef router16 = getContext().actorOf(new BroadcastGroup(paths).props(), "router16");
    // #broadcast-group-2

    // #scatter-gather-pool-1
    ActorRef router17 =
        getContext()
            .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router17");
    // #scatter-gather-pool-1

    // #scatter-gather-pool-2
    Duration within = Duration.ofSeconds(10);
    ActorRef router18 =
        getContext()
            .actorOf(
                new ScatterGatherFirstCompletedPool(5, within).props(Props.create(Worker.class)),
                "router18");
    // #scatter-gather-pool-2

    // #scatter-gather-group-1
    ActorRef router19 = getContext().actorOf(FromConfig.getInstance().props(), "router19");
    // #scatter-gather-group-1

    // #scatter-gather-group-2
    Duration within2 = Duration.ofSeconds(10);
    ActorRef router20 =
        getContext()
            .actorOf(new ScatterGatherFirstCompletedGroup(paths, within2).props(), "router20");
    // #scatter-gather-group-2

    // #tail-chopping-pool-1
    ActorRef router21 =
        getContext()
            .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router21");
    // #tail-chopping-pool-1

    // #tail-chopping-pool-2
    Duration within3 = Duration.ofSeconds(10);
    Duration interval = Duration.ofMillis(20);
    ActorRef router22 =
        getContext()
            .actorOf(
                new TailChoppingPool(5, within3, interval).props(Props.create(Worker.class)),
                "router22");
    // #tail-chopping-pool-2

    // #tail-chopping-group-1
    ActorRef router23 = getContext().actorOf(FromConfig.getInstance().props(), "router23");
    // #tail-chopping-group-1

    // #tail-chopping-group-2
    Duration within4 = Duration.ofSeconds(10);
    Duration interval2 = Duration.ofMillis(20);
    ActorRef router24 =
        getContext().actorOf(new TailChoppingGroup(paths, within4, interval2).props(), "router24");
    // #tail-chopping-group-2

    // #consistent-hashing-pool-1
    ActorRef router25 =
        getContext()
            .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router25");
    // #consistent-hashing-pool-1

    // #consistent-hashing-pool-2
    ActorRef router26 =
        getContext()
            .actorOf(new ConsistentHashingPool(5).props(Props.create(Worker.class)), "router26");
    // #consistent-hashing-pool-2

    // #consistent-hashing-group-1
    ActorRef router27 = getContext().actorOf(FromConfig.getInstance().props(), "router27");
    // #consistent-hashing-group-1

    // #consistent-hashing-group-2
    ActorRef router28 = getContext().actorOf(new ConsistentHashingGroup(paths).props(), "router28");
    // #consistent-hashing-group-2

    // #resize-pool-1
    ActorRef router29 =
        getContext()
            .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router29");
    // #resize-pool-1

    // #resize-pool-2
    DefaultResizer resizer = new DefaultResizer(2, 15);
    ActorRef router30 =
        getContext()
            .actorOf(
                new RoundRobinPool(5).withResizer(resizer).props(Props.create(Worker.class)),
                "router30");
    // #resize-pool-2

    // #optimal-size-exploring-resize-pool
    ActorRef router31 =
        getContext()
            .actorOf(FromConfig.getInstance().props(Props.create(Worker.class)), "router31");
    // #optimal-size-exploring-resize-pool

    @Override
    public Receive createReceive() {
      return receiveBuilder().build();
    }
  }

  @Test
  public void createActors() {
    // #create-workers
    system.actorOf(Props.create(Workers.class), "workers");
    // #create-workers

    // #create-parent
    system.actorOf(Props.create(Parent.class), "parent");
    // #create-parent
  }

  @Test
  public void demonstrateDispatcher() {
    // #dispatchers
    Props props =
        // “head” router actor will run on "router-dispatcher" dispatcher
        // Worker routees will run on "pool-dispatcher" dispatcher
        new RandomPool(5).withDispatcher("router-dispatcher").props(Props.create(Worker.class));
    ActorRef router = system.actorOf(props, "poolWithDispatcher");
    // #dispatchers
  }

  @Test
  public void demonstrateBroadcast() {
    new TestKit(system) {
      {
        ActorRef router = system.actorOf(new RoundRobinPool(5).props(Props.create(Echo.class)));
        // #broadcastDavyJonesWarning
        router.tell(new Broadcast("Watch out for Davy Jones' locker"), getTestActor());
        // #broadcastDavyJonesWarning
        assertEquals(5, receiveN(5).size());
      }
    };
  }

  @Test
  public void demonstratePoisonPill() {
    new TestKit(system) {
      {
        ActorRef router =
            watch(system.actorOf(new RoundRobinPool(5).props(Props.create(Echo.class))));
        // #poisonPill
        router.tell(PoisonPill.getInstance(), getTestActor());
        // #poisonPill
        expectTerminated(router);
      }
    };
  }

  @Test
  public void demonstrateBroadcastPoisonPill() {
    new TestKit(system) {
      {
        ActorRef router =
            watch(system.actorOf(new RoundRobinPool(5).props(Props.create(Echo.class))));
        // #broadcastPoisonPill
        router.tell(new Broadcast(PoisonPill.getInstance()), getTestActor());
        // #broadcastPoisonPill
        expectTerminated(router);
      }
    };
  }

  @Test
  public void demonstrateKill() {
    new TestKit(system) {
      {
        ActorRef router =
            watch(system.actorOf(new RoundRobinPool(5).props(Props.create(Echo.class))));
        // #kill
        router.tell(Kill.getInstance(), getTestActor());
        // #kill
        expectTerminated(router);
      }
    };
  }

  @Test
  public void demonstrateBroadcastKill() {
    new TestKit(system) {
      {
        ActorRef router =
            watch(system.actorOf(new RoundRobinPool(5).props(Props.create(Echo.class))));
        // #broadcastKill
        router.tell(new Broadcast(Kill.getInstance()), getTestActor());
        // #broadcastKill
        expectTerminated(router);
      }
    };
  }

  @Test
  public void demonstrateRemoteDeploy() {
    // #remoteRoutees
    Address[] addresses = {
      new Address("pekko", "remotesys", "otherhost", 1234),
      AddressFromURIString.parse("pekko://othersys@anotherhost:1234")
    };
    ActorRef routerRemote =
        system.actorOf(
            new RemoteRouterConfig(new RoundRobinPool(5), addresses)
                .props(Props.create(Echo.class)));
    // #remoteRoutees
  }

  // only compile
  public void demonstrateRemoteDeployWithArtery() {
    // #remoteRoutees-artery
    Address[] addresses = {
      new Address("pekko", "remotesys", "otherhost", 1234),
      AddressFromURIString.parse("pekko://othersys@anotherhost:1234")
    };
    ActorRef routerRemote =
        system.actorOf(
            new RemoteRouterConfig(new RoundRobinPool(5), addresses)
                .props(Props.create(Echo.class)));
    // #remoteRoutees-artery
  }

  @Test
  public void demonstrateSupervisor() {
    // #supervision
    final SupervisorStrategy strategy =
        new OneForOneStrategy(
            5,
            Duration.ofMinutes(1),
            Collections.<Class<? extends Throwable>>singletonList(Exception.class));
    final ActorRef router =
        system.actorOf(
            new RoundRobinPool(5).withSupervisorStrategy(strategy).props(Props.create(Echo.class)));
    // #supervision
  }
}
