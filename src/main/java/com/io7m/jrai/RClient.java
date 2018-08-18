package com.io7m.jrai;

import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.UtilSSLSocketFactory;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.ConnectAttemptFailedEvent;
import org.pircbotx.hooks.events.ConnectEvent;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.output.OutputIRC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A relay client.
 */

public final class RClient extends ListenerAdapter implements AutoCloseable
{
  private static final Logger LOG = LoggerFactory.getLogger(RClient.class);

  private final RConfiguration configuration;
  private final PircBotX bot;
  private final List<RClientBrokerTask> tasks;
  private final ExecutorService executor;
  private final AtomicBoolean done = new AtomicBoolean(false);

  @Override
  public void close()
    throws Exception
  {
    if (this.done.compareAndSet(false, true)) {
      Exception e = null;

      for (final RClientBrokerTask task : this.tasks) {
        try {
          task.close();
        } catch (final IOException ex) {
          if (e == null) {
            e = new Exception(ex);
          } else {
            e.addSuppressed(ex);
          }
        }
      }

      this.executor.shutdown();

      if (e != null) {
        throw e;
      }
    }
  }

  private static final class RClientBrokerTask implements Closeable, Runnable
  {
    private final RClient client;
    private final RQueueConfiguration queue_configuration;
    private RBrokerConnection connection;

    RClientBrokerTask(
      final RClient in_client,
      final RQueueConfiguration in_queue_configuration)
    {
      this.client =
        Objects.requireNonNull(in_client, "client");
      this.queue_configuration =
        Objects.requireNonNull(in_queue_configuration, "queue_configuration");
    }

    @Override
    public void close()
      throws IOException
    {
      this.connection.close();
    }

    @Override
    public void run()
    {
      LOG.debug("starting task for queue: {}", this.queue_configuration.queueAddress());

      while (!this.client.done.get()) {
        try {
          if (this.connection == null) {
            this.connection = RBrokerConnection.create(this.queue_configuration);
          }

          this.connection.receive(this::onMessageReceived);
        } catch (final Exception e) {
          LOG.error("i/o error: ", e);
          try {
            if (this.connection != null) {
              this.connection.close();
            }
          } catch (final IOException ex) {
            LOG.error("error closing connection: ", ex);
          } finally {
            this.connection = null;
          }
        }
      }
    }

    private void onMessageReceived(
      final RMessage message)
    {
      LOG.debug("received: {}: {}", message.queue(), message.message());

      final OutputIRC sender = this.client.bot.send();

      sender.message(
        this.client.configuration.ircChannel(),
        message.queue() + ": " + message.message());
    }
  }

  private RClient(
    final RConfiguration in_configuration)
  {
    this.configuration = Objects.requireNonNull(in_configuration, "configuration");

    this.tasks = new ArrayList<>();
    this.executor = Executors.newFixedThreadPool(
      this.configuration.queues().size(),
      runnable -> {
        final Thread thread = new Thread(runnable);
        thread.setName("com.io7m.jrai." + thread.getId());
        return thread;
      });

    final UtilSSLSocketFactory tls_factory = new UtilSSLSocketFactory();

    final Configuration.Builder irc_configuration_builder = new Configuration.Builder();
    irc_configuration_builder.addServer(
      this.configuration.ircHost(), this.configuration.ircPort());
    irc_configuration_builder.addAutoJoinChannel(
      this.configuration.ircChannel());
    irc_configuration_builder.setName(
      this.configuration.ircUserName());
    irc_configuration_builder.setRealName(
      this.configuration.ircUserName());
    irc_configuration_builder.setVersion("jrai");
    irc_configuration_builder.setAutoReconnect(true);
    irc_configuration_builder.setSocketFactory(tls_factory);
    irc_configuration_builder.setLogin(
      this.configuration.ircUserName());

    irc_configuration_builder.addListener(this);

    final Configuration irc_configuration = irc_configuration_builder.buildConfiguration();
    this.bot = new PircBotX(irc_configuration);
  }

  /**
   * Create a relay client.
   *
   * @param configuration The configuration
   *
   * @return A new client
   */

  public static RClient create(
    final RConfiguration configuration)
  {
    return new RClient(configuration);
  }

  /**
   * Open a connection to the IRC server.
   *
   * @throws Exception On errors
   */

  public void start()
    throws Exception
  {
    this.bot.startBot();
  }

  @Override
  public void onConnect(
    final ConnectEvent event)
    throws Exception
  {
    super.onConnect(event);
    LOG.debug("connected");
  }

  @Override
  public void onJoin(final JoinEvent event)
    throws Exception
  {
    super.onJoin(event);

    LOG.debug("joined: {}", event.getChannel().getName());

    for (final RQueueConfiguration queue_configuration : this.configuration.queues()) {
      final RClientBrokerTask task =
        new RClientBrokerTask(this, queue_configuration);
      this.tasks.add(task);
      this.executor.execute(task);
    }
  }

  @Override
  public void onConnectAttemptFailed(
    final ConnectAttemptFailedEvent event)
    throws Exception
  {
    super.onConnectAttemptFailed(event);

    event.getConnectExceptions().forEach(
      (address, exception) ->
        LOG.error(
          "connection failed: {} - {}",
          address,
          exception.getClass().getCanonicalName(),
          exception.getMessage()));
  }

  @Override
  public void onDisconnect(
    final DisconnectEvent event)
    throws Exception
  {
    super.onDisconnect(event);

    final Exception ex = event.getDisconnectException();
    if (ex != null) {
      LOG.info(
        "disconnected: {} - {}",
        ex.getClass().getCanonicalName(),
        ex.getMessage());
    } else {
      LOG.info("disconnected: (no exception information available)");
    }
  }
}
