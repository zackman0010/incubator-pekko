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

package jdocs.stream.io;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.javadsl.Framing;
import jdocs.AbstractJavaTest;
import jdocs.stream.SilenceSystemOut;
import org.apache.pekko.testkit.javadsl.TestKit;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.*;
import org.apache.pekko.stream.javadsl.Tcp.*;
import org.apache.pekko.testkit.SocketUtil;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.util.ByteString;

public class StreamTcpDocTest extends AbstractJavaTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("StreamTcpDocTest");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  final SilenceSystemOut.System System = SilenceSystemOut.get();

  private final ConcurrentLinkedQueue<String> input = new ConcurrentLinkedQueue<>();

  {
    input.add("Hello world");
    input.add("What a lovely day");
  }

  private String readLine(String prompt) {
    String s = input.poll();
    return (s == null ? "q" : s);
  }

  @Test
  public void demonstrateSimpleServerConnection() {
    {
      // #echo-server-simple-bind
      // IncomingConnection and ServerBinding imported from Tcp
      final Source<IncomingConnection, CompletionStage<ServerBinding>> connections =
          Tcp.get(system).bind("127.0.0.1", 8888);
      // #echo-server-simple-bind
    }
    {
      final InetSocketAddress localhost = SocketUtil.temporaryServerAddress("127.0.0.1", false);
      final Source<IncomingConnection, CompletionStage<ServerBinding>> connections =
          Tcp.get(system).bind(localhost.getHostString(), localhost.getPort());

      // #echo-server-simple-handle
      connections.runForeach(
          connection -> {
            System.out.println("New connection from: " + connection.remoteAddress());

            final Flow<ByteString, ByteString, NotUsed> echo =
                Flow.of(ByteString.class)
                    .via(
                        Framing.delimiter(
                            ByteString.fromString("\n"), 256, FramingTruncation.DISALLOW))
                    .map(ByteString::utf8String)
                    .map(s -> s + "!!!\n")
                    .map(ByteString::fromString);

            connection.handleWith(echo, system);
          },
          system);
      // #echo-server-simple-handle
    }
  }

  @Test
  public void actuallyWorkingClientServerApp() throws Exception {

    final InetSocketAddress localhost = SocketUtil.temporaryServerAddress("127.0.0.1", false);
    final TestProbe serverProbe = new TestProbe(system);

    final Source<IncomingConnection, CompletionStage<ServerBinding>> connections =
        Tcp.get(system).bind(localhost.getHostString(), localhost.getPort());
    final CompletionStage<ServerBinding> bindingCS =
        // #welcome-banner-chat-server
        connections
            .to(
                Sink.foreach(
                    (IncomingConnection connection) -> {
                      // server logic, parses incoming commands
                      final Flow<String, String, NotUsed> commandParser =
                          Flow.<String>create()
                              .takeWhile(elem -> !elem.equals("BYE"))
                              .map(elem -> elem + "!");

                      final String welcomeMsg =
                          "Welcome to: "
                              + connection.localAddress()
                              + " you are: "
                              + connection.remoteAddress()
                              + "!";

                      final Source<String, NotUsed> welcome = Source.single(welcomeMsg);
                      final Flow<ByteString, ByteString, NotUsed> serverLogic =
                          Flow.of(ByteString.class)
                              .via(
                                  Framing.delimiter(
                                      ByteString.fromString("\n"), 256, FramingTruncation.DISALLOW))
                              .map(ByteString::utf8String)
                              // #welcome-banner-chat-server
                              .map(
                                  command -> {
                                    serverProbe.ref().tell(command, null);
                                    return command;
                                  })
                              // #welcome-banner-chat-server
                              .via(commandParser)
                              .merge(welcome)
                              .map(s -> s + "\n")
                              .map(ByteString::fromString);

                      connection.handleWith(serverLogic, system);
                    }))
            .run(system);
    // #welcome-banner-chat-server

    // make sure server is bound before we do anything else
    bindingCS.toCompletableFuture().get(3, TimeUnit.SECONDS);

    {
      // just for docs, never actually used
      // #repl-client
      final Flow<ByteString, ByteString, CompletionStage<OutgoingConnection>> connection =
          Tcp.get(system).outgoingConnection("127.0.0.1", 8888);
      // #repl-client
    }

    {
      final Flow<ByteString, ByteString, CompletionStage<OutgoingConnection>> connection =
          Tcp.get(system).outgoingConnection(localhost.getHostString(), localhost.getPort());
      // #repl-client
      final Flow<String, ByteString, NotUsed> replParser =
          Flow.<String>create()
              .takeWhile(elem -> !elem.equals("q"))
              .concat(Source.single("BYE")) // will run after the original flow completes
              .map(elem -> ByteString.fromString(elem + "\n"));

      final Flow<ByteString, ByteString, NotUsed> repl =
          Flow.of(ByteString.class)
              .via(Framing.delimiter(ByteString.fromString("\n"), 256, FramingTruncation.DISALLOW))
              .map(ByteString::utf8String)
              .map(
                  text -> {
                    System.out.println("Server: " + text);
                    return "next";
                  })
              .map(elem -> readLine("> "))
              .via(replParser);

      CompletionStage<OutgoingConnection> connectionCS = connection.join(repl).run(system);
      // #repl-client

      // make sure it got connected (or fails the test)
      connectionCS.toCompletableFuture().get(5L, TimeUnit.SECONDS);
    }

    serverProbe.expectMsg("Hello world");
    serverProbe.expectMsg("What a lovely day");
    serverProbe.expectMsg("BYE");
  }
}
