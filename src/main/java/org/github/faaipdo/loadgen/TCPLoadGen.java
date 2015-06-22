package org.github.faaipdo.loadgen;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import org.apache.commons.io.IOUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * TCP load generator. see LoadGenOptions for options, only argument is a file with the payload for all requests.
 */
public final class TCPLoadGen {

    private static final Logger LOG = LoggerFactory.getLogger(TCPLoadGen.class);

    public static final MetricRegistry METRICS = new MetricRegistry();

    private TCPLoadGen() {
        //no instances
    }

    public static void main(String[] args) throws Exception {
        //parse command line args
        final LoadGenOptions options = new LoadGenOptions();
        CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException cle) {
            parser.printUsage(System.err);
            return;
        }

        //bootstrap netty
        final EventLoopGroup group = Epoll.isAvailable() ? new EpollEventLoopGroup(options.getThreads()) : new NioEventLoopGroup(options.getThreads());
        //install shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread("ShutdownHook") {
            @Override
            public void run() {
                super.run();
                LOG.info("Shutting down");
                shutdownNetty(group);
            }
        });

        try (InputStream in = new FileInputStream(options.getInput())) {
            //init the handler that generates requests
            final byte[] payload = IOUtils.toByteArray(in);

            LoadGenHandlerManager manager = LoadGenHandlerManager.initConfig(
                    payload,
                    options.getHost(),
                    options.getPort(),
                    Epoll.isAvailable() ? EpollSocketChannel.class : NioSocketChannel.class
            );

            //init NUM_OF_CONNECTIONS channels
            LOG.info("loadgen running on {} connection(s).", options.getConnections());
            for (int i = 0; i < options.getConnections(); i++) {
                manager.connectAndRun(group);
            }
            startReport();
            //Shutdown in (options.duration) and sync on that future
            Future f = group.next().scheduleWithFixedDelay(
                    () -> shutdownNetty(group),
                    options.getDuration() - 1, options.getDuration() - 1, TimeUnit.SECONDS);

            //wait until group is shut down
            f.syncUninterruptibly();
        } finally {
            if (!group.isShutdown()) {
                group.shutdownGracefully();
            }
        }
    }

    private static void shutdownNetty(EventLoopGroup group) {
        group.shutdownGracefully(1, 1, TimeUnit.SECONDS);
    }

    private static void startReport() {
        ConsoleReporter reporter = ConsoleReporter
                .forRegistry(METRICS)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(3, TimeUnit.SECONDS);
    }
}