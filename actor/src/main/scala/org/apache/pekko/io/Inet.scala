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

package org.apache.pekko.io

import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.DatagramChannel

import org.apache.pekko
import pekko.util.unused

object Inet {

  /**
   * SocketOption is a package of data (from the user) and associated
   * behavior (how to apply that to a channel).
   */
  trait SocketOption {

    /**
     * Action to be taken for this option before bind() is called
     */
    def beforeDatagramBind(@unused ds: DatagramSocket): Unit = ()

    /**
     * Action to be taken for this option before bind() is called
     */
    def beforeServerSocketBind(@unused ss: ServerSocket): Unit = ()

    /**
     * Action to be taken for this option before calling connect()
     */
    def beforeConnect(@unused s: Socket): Unit = ()

    /**
     * Action to be taken for this option after connect returned.
     */
    def afterConnect(@unused s: Socket): Unit = ()
  }

  /**
   * Java API: AbstractSocketOption is a package of data (from the user) and associated
   * behavior (how to apply that to a channel).
   */
  abstract class AbstractSocketOption extends SocketOption

  trait SocketOptionV2 extends SocketOption {

    /**
     * Action to be taken for this option after connect returned.
     */
    def afterBind(@unused s: DatagramSocket): Unit = ()

    /**
     * Action to be taken for this option after connect returned.
     */
    def afterBind(@unused s: ServerSocket): Unit = ()

    /**
     * Action to be taken for this option after connect returned.
     */
    def afterConnect(@unused s: DatagramSocket): Unit = ()

  }

  /**
   * Java API
   */
  abstract class AbstractSocketOptionV2 extends SocketOptionV2

  /**
   * DatagramChannel creation behavior.
   */
  class DatagramChannelCreator extends SocketOption {

    /**
     * Open and return new DatagramChannel.
     *
     * `throws` is needed because `DatagramChannel.open` method
     * can throw an exception.
     */
    @throws(classOf[Exception])
    def create(): DatagramChannel = DatagramChannel.open()
  }

  object DatagramChannelCreator {
    val default = new DatagramChannelCreator()
    def apply() = default
  }

  object SO {

    /**
     * [[pekko.io.Inet.SocketOption]] to set the SO_RCVBUF option
     *
     * For more information see [[java.net.Socket#setReceiveBufferSize]]
     */
    final case class ReceiveBufferSize(size: Int) extends SocketOption {
      require(size > 0, "ReceiveBufferSize must be > 0")
      override def beforeServerSocketBind(s: ServerSocket): Unit = s.setReceiveBufferSize(size)
      override def beforeDatagramBind(s: DatagramSocket): Unit = s.setReceiveBufferSize(size)
      override def beforeConnect(s: Socket): Unit = s.setReceiveBufferSize(size)
    }

    // server socket options

    /**
     * [[pekko.io.Inet.SocketOption]] to enable or disable SO_REUSEADDR
     *
     * For more information see [[java.net.Socket#setReuseAddress]]
     */
    final case class ReuseAddress(on: Boolean) extends SocketOption {
      override def beforeServerSocketBind(s: ServerSocket): Unit = s.setReuseAddress(on)
      override def beforeDatagramBind(s: DatagramSocket): Unit = s.setReuseAddress(on)
      override def beforeConnect(s: Socket): Unit = s.setReuseAddress(on)
    }

    /**
     * [[pekko.io.Inet.SocketOption]] to set the SO_SNDBUF option.
     *
     * For more information see [[java.net.Socket#setSendBufferSize]]
     */
    final case class SendBufferSize(size: Int) extends SocketOption {
      require(size > 0, "SendBufferSize must be > 0")
      override def afterConnect(s: Socket): Unit = s.setSendBufferSize(size)
    }

    /**
     * [[pekko.io.Inet.SocketOption]] to set the traffic class or
     * type-of-service octet in the IP header for packets sent from this
     * socket.
     *
     * For more information see [[java.net.Socket#setTrafficClass]]
     */
    final case class TrafficClass(tc: Int) extends SocketOption {
      require(0 <= tc && tc <= 255, "TrafficClass needs to be in the interval [0, 255]")
      override def afterConnect(s: Socket): Unit = s.setTrafficClass(tc)
    }

  }

  trait SoForwarders {

    /**
     * [[pekko.io.Inet.SocketOption]] to set the SO_RCVBUF option
     *
     * For more information see [[java.net.Socket#setReceiveBufferSize]]
     */
    val ReceiveBufferSize = SO.ReceiveBufferSize

    /**
     * [[pekko.io.Inet.SocketOption]] to enable or disable SO_REUSEADDR
     *
     * For more information see [[java.net.Socket#setReuseAddress]]
     */
    val ReuseAddress = SO.ReuseAddress

    /**
     * [[pekko.io.Inet.SocketOption]] to set the SO_SNDBUF option.
     *
     * For more information see [[java.net.Socket#setSendBufferSize]]
     */
    val SendBufferSize = SO.SendBufferSize

    /**
     * [[pekko.io.Inet.SocketOption]] to set the traffic class or
     * type-of-service octet in the IP header for packets sent from this
     * socket.
     *
     * For more information see [[java.net.Socket#setTrafficClass]]
     */
    val TrafficClass = SO.TrafficClass
  }

  trait SoJavaFactories {
    import SO._

    /**
     * [[pekko.io.Inet.SocketOption]] to set the SO_RCVBUF option
     *
     * For more information see [[java.net.Socket#setReceiveBufferSize]]
     */
    def receiveBufferSize(size: Int) = ReceiveBufferSize(size)

    /**
     * [[pekko.io.Inet.SocketOption]] to enable or disable SO_REUSEADDR
     *
     * For more information see [[java.net.Socket#setReuseAddress]]
     */
    def reuseAddress(on: Boolean) = ReuseAddress(on)

    /**
     * [[pekko.io.Inet.SocketOption]] to set the SO_SNDBUF option.
     *
     * For more information see [[java.net.Socket#setSendBufferSize]]
     */
    def sendBufferSize(size: Int) = SendBufferSize(size)

    /**
     * [[pekko.io.Inet.SocketOption]] to set the traffic class or
     * type-of-service octet in the IP header for packets sent from this
     * socket.
     *
     * For more information see [[java.net.Socket#setTrafficClass]]
     */
    def trafficClass(tc: Int) = TrafficClass(tc)
  }

}
