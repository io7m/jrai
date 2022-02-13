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

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Functions to parse configurations.
 */

public final class RConfigurations
{
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private RConfigurations()
  {

  }

  /**
   * Parse a configuration from the given properties.
   *
   * @param properties The input properties
   *
   * @return A parsed message
   *
   * @throws IOException On errors
   */

  public static RConfiguration ofProperties(
    final Properties properties)
    throws IOException
  {
    Objects.requireNonNull(properties, "properties");

    try {
      final var builder =
        RConfiguration.builder();
      final var queues_value =
        properties.getProperty("com.io7m.jrai.queues");

      builder.setIrcHost(properties.getProperty("com.io7m.jrai.irc_server_host"));
      builder.setIrcPort(Integer.parseInt(properties.getProperty("com.io7m.jrai.irc_server_port")));
      builder.setIrcNickName(properties.getProperty("com.io7m.jrai.irc_nick"));
      builder.setIrcUserName(properties.getProperty("com.io7m.jrai.irc_user"));
      builder.setIrcChannel(properties.getProperty("com.io7m.jrai.irc_channel"));

      for (final var queue : WHITESPACE.split(queues_value)) {
        builder.addQueues(parseQueue(properties, queue));
      }

      return builder.build();
    } catch (final Exception e) {
      throw new IOException(e);
    }
  }

  private static RQueueConfiguration parseQueue(
    final Properties properties,
    final String directory)
  {
    final var builder =
      RQueueConfiguration.builder();

    builder.setQueueAddress(properties.getProperty(
      String.format("com.io7m.jrai.queues.%s.queue_address", directory)));
    builder.setBrokerUser(properties.getProperty(
      String.format("com.io7m.jrai.queues.%s.broker_user", directory)));
    builder.setBrokerPassword(properties.getProperty(
      String.format("com.io7m.jrai.queues.%s.broker_password", directory)));
    builder.setBrokerAddress(properties.getProperty(
      String.format("com.io7m.jrai.queues.%s.broker_address", directory)));
    builder.setBrokerPort(Integer.parseInt(properties.getProperty(
      String.format("com.io7m.jrai.queues.%s.broker_port", directory))));

    return builder.build();
  }
}
