package org.github.faaipdo.loadgen;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.File;

/**
 * Command line options for TCPLoadgen.
 */
public class LoadGenOptions {

    @Option(name = "-h", required = false, usage = "Hostname or IP of host to benchmark")
    private String host;

    @Option(name = "-p", usage = "port to connect to (default 80)")
    private int port = 80;

    @Option(name = "-d", usage = "duration of benchmark in seconds (default 120)")
    private int duration = 120;

    @Option(name = "-t", usage = "number of IO threads (default 0 (let netty decide))")
    private int threads;

    @Option(name = "-c", usage = "number of parallel connections (default 8)")
    private int connections = 8;

    @Argument(required = false, usage = "input file with payload")
    private File input;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getConnections() {
        return connections;
    }

    public void setConnections(int connections) {
        this.connections = connections;
    }

    public File getInput() {
        return input;
    }

    public void setInput(File input) {
        this.input = input;
    }
}
