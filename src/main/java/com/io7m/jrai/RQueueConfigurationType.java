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

import com.io7m.immutables.styles.ImmutablesStyleType;
import org.immutables.value.Value;

/**
 * A queue configuration.
 */

@ImmutablesStyleType
@Value.Immutable
public interface RQueueConfigurationType
{
  /**
   * @return The queue address
   */

  @Value.Parameter
  String queueAddress();

  /**
   * @return The user account on the message broker
   */

  @Value.Parameter
  String brokerUser();

  /**
   * @return The user account password on the message broker
   */

  @Value.Parameter
  String brokerPassword();

  /**
   * @return The address of the message broker
   */

  @Value.Parameter
  String brokerAddress();

  /**
   * @return The port used on the message broker
   */

  @Value.Parameter
  int brokerPort();
}
