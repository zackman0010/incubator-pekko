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

package docs.io

import java.net.{ InetAddress, InetSocketAddress, NetworkInterface, StandardProtocolFamily }
import java.net.DatagramSocket
import java.nio.channels.DatagramChannel

import org.apache.pekko.actor.{ Actor, ActorLogging, ActorRef }
import org.apache.pekko.io.Inet.{ DatagramChannelCreator, SocketOptionV2 }
import org.apache.pekko.io.{ IO, Udp }
import org.apache.pekko.util.ByteString

//#inet6-protocol-family
final case class Inet6ProtocolFamily() extends DatagramChannelCreator {
  override def create() =
    DatagramChannel.open(StandardProtocolFamily.INET6)
}
//#inet6-protocol-family

//#multicast-group
final case class MulticastGroup(address: String, interface: String) extends SocketOptionV2 {
  override def afterBind(s: DatagramSocket): Unit = {
    val group = InetAddress.getByName(address)
    val networkInterface = NetworkInterface.getByName(interface)
    s.getChannel.join(group, networkInterface)
  }
}
//#multicast-group

class Listener(iface: String, group: String, port: Int, sink: ActorRef) extends Actor with ActorLogging {
  // #bind
  import context.system
  val opts = List(Inet6ProtocolFamily(), MulticastGroup(group, iface))
  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(port), opts)
  // #bind

  def receive = {
    case b @ Udp.Bound(to) =>
      log.info("Bound to {}", to)
      sink ! b
    case Udp.Received(data, remote) =>
      val msg = data.decodeString("utf-8")
      log.info("Received '{}' from {}", msg, remote)
      sink ! msg
  }
}

class Sender(iface: String, group: String, port: Int, msg: String) extends Actor with ActorLogging {
  import context.system
  IO(Udp) ! Udp.SimpleSender(List(Inet6ProtocolFamily()))

  def receive = {
    case Udp.SimpleSenderReady => {
      val remote = new InetSocketAddress(s"$group%$iface", port)
      log.info("Sending message to {}", remote)
      sender() ! Udp.Send(ByteString(msg), remote)
    }
  }
}
