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

package jdoc.org.apache.pekko.serialization.jackson.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import jdoc.org.apache.pekko.serialization.jackson.MySerializable;

// #rename-class
public class OrderAdded implements MySerializable {
  public final String shoppingCartId;

  @JsonCreator
  public OrderAdded(String shoppingCartId) {
    this.shoppingCartId = shoppingCartId;
  }
}
// #rename-class
