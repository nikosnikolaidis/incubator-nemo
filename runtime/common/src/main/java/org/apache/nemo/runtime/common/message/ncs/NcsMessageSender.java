/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.nemo.runtime.common.message.ncs;

import org.apache.nemo.runtime.common.ReplyFutureMap;
import org.apache.nemo.runtime.common.comm.ControlMessage;
import org.apache.nemo.runtime.common.message.MessageSender;
import org.apache.reef.io.network.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * MessageSender for NCS.
 */
final class NcsMessageSender implements MessageSender<ControlMessage.Message> {
  private static final Logger LOG = LoggerFactory.getLogger(NcsMessageSender.class.getName());

  private final Connection<ControlMessage.Message> connection;
  private final ReplyFutureMap<ControlMessage.Message> replyFutureMap;

  NcsMessageSender(
    final Connection<ControlMessage.Message> connection,
    final ReplyFutureMap replyFutureMap) {
    this.connection = connection;
    this.replyFutureMap = replyFutureMap;
  }

  @Override
  public void send(final ControlMessage.Message message) {
    connection.write(message);
  }

  @Override
  public CompletableFuture<ControlMessage.Message> request(final ControlMessage.Message message) {
    final CompletableFuture<ControlMessage.Message> future = replyFutureMap.beforeRequest(message.getId());
    connection.write(message);
    return future;
  }

  @Override
  public void close() throws Exception {
    connection.close();
  }
}
