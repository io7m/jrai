/*
 * Copyright © 2018 Mark Raynsford <code@io7m.com> http://io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.io7m.jrai;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A connection to a message broker.
 */

public final class RBrokerConnection implements Closeable
{
  private final RQueueConfiguration configuration;
  private final ServerLocator locator;
  private final ClientSessionFactory clients;
  private final ClientSession session;
  private final ClientConsumer consumer;
  private final AtomicBoolean closed;

  private RBrokerConnection(
    final RQueueConfiguration in_configuration,
    final ServerLocator in_locator,
    final ClientSessionFactory in_clients,
    final ClientSession in_session,
    final ClientConsumer in_consumer)
  {
    this.configuration =
      Objects.requireNonNull(in_configuration, "configuration");
    this.locator =
      Objects.requireNonNull(in_locator, "locator");
    this.clients =
      Objects.requireNonNull(in_clients, "clients");
    this.session =
      Objects.requireNonNull(in_session, "session");
    this.consumer =
      Objects.requireNonNull(in_consumer, "consumer");
    this.closed = new AtomicBoolean(false);
  }

  /**
   * Create a new connection.
   *
   * @param configuration The configuration
   *
   * @return A new connection
   *
   * @throws Exception On errors
   */

  public static RBrokerConnection create(
    final RQueueConfiguration configuration)
    throws Exception
  {
    final String address =
      new StringBuilder(64)
        .append("tcp://")
        .append(configuration.brokerAddress())
        .append(":")
        .append(configuration.brokerPort())
        .append("?sslEnabled=true")
        .toString();

    final ServerLocator locator =
      ActiveMQClient.createServerLocator(address);
    final ClientSessionFactory clients =
      locator.createSessionFactory();
    final ClientSession session =
      clients.createSession(
        configuration.brokerUser(),
        configuration.brokerPassword(),
        false,
        false,
        false,
        false,
        1);

    final ClientConsumer consumer =
      session.createConsumer(configuration.queueAddress());

    session.start();
    return new RBrokerConnection(configuration, locator, clients, session, consumer);
  }

  public void receive(
    final Consumer<RMessage> receiver)
    throws IOException
  {
    try {
      Objects.requireNonNull(receiver, "receiver");
      final ClientMessage message = this.consumer.receive(500L);

      if (message != null) {
        final int size = message.getBodySize();
        final byte[] bytes = new byte[size];
        final ActiveMQBuffer buffer = message.getBodyBuffer();
        buffer.readBytes(bytes);
        final String text = new String(bytes, UTF_8);

        receiver.accept(
          RMessage.builder()
            .setQueue(this.configuration.queueAddress())
            .setMessage(text)
            .build());

        message.acknowledge();
        this.session.commit();
      }
    } catch (final ActiveMQException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void close()
    throws IOException
  {
    if (this.closed.compareAndSet(false, true)) {
      IOException exception = null;

      try {
        this.session.close();
      } catch (final ActiveMQException e) {
        exception = new IOException("Failed to close resources");
        exception.addSuppressed(e);
      }
      try {
        this.consumer.close();
      } catch (final ActiveMQException e) {
        if (exception == null) {
          exception = new IOException("Failed to close resources");
        }
        exception.addSuppressed(e);
      }
      this.clients.close();
      this.locator.close();

      if (exception != null) {
        throw exception;
      }
    }
  }
}
