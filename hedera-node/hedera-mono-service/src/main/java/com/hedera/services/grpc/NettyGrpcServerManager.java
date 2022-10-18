/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.grpc;

import static com.hedera.services.utils.SleepingPause.SLEEPING_PAUSE;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.utils.Pause;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class NettyGrpcServerManager implements GrpcServerManager {
    private static final Logger log = LogManager.getLogger(NettyGrpcServerManager.class);

    private static final long TIME_TO_AWAIT_TERMINATION = 5;

    private Server server;
    private Server tlsServer;

    private final int startRetries;
    private final long startRetryIntervalMs;
    private final Consumer<Thread> hookAdder;
    private final Set<BindableService> bindableServices;
    private final ConfigDrivenNettyFactory nettyBuilder;

    @Inject
    public NettyGrpcServerManager(
            Consumer<Thread> hookAdder,
            NodeLocalProperties nodeProperties,
            Set<BindableService> bindableServices,
            ConfigDrivenNettyFactory nettyBuilder) {
        this.hookAdder = hookAdder;
        this.nettyBuilder = nettyBuilder;
        this.bindableServices = bindableServices;

        startRetries = nodeProperties.nettyStartRetries();
        startRetryIntervalMs = nodeProperties.nettyStartRetryIntervalMs();
    }

    @Override
    public void start(int port, int tlsPort, Consumer<String> println) {
        try {
            hookAdder.accept(new Thread(() -> terminateNetty(port, tlsPort, println)));
            server = startOneNettyServer(false, port, println, SLEEPING_PAUSE);
            tlsServer = startOneNettyServer(true, tlsPort, println, SLEEPING_PAUSE);
        } catch (FileNotFoundException fnfe) {
            tlsServer = null;
            String message = nettyAction("Could not start", true, tlsPort, false);
            log.warn("{} ({}).", message, fnfe.getMessage());
            println.accept(message);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Server startOneNettyServer(boolean sslEnabled, int port, Consumer<String> println, Pause pause)
            throws IOException {
        println.accept(nettyAction("Starting", sslEnabled, port, true));

        NettyServerBuilder builder = nettyBuilder.builderFor(port, sslEnabled);
        bindableServices.forEach(builder::addService);
        Server nettyServer = builder.build();

        var retryNo = 1;
        final var n = Math.max(0, startRetries);
        for (; retryNo <= n; retryNo++) {
            try {
                nettyServer.start();
                break;
            } catch (IOException e) {
                final var summaryMsg = nettyAction("Still trying to start", sslEnabled, port, true);
                log.warn("(Attempts=" + retryNo + ") " + summaryMsg + e.getMessage());
                pause.forMs(startRetryIntervalMs);
            }
        }
        if (retryNo == n + 1) {
            nettyServer.start();
        }

        println.accept(nettyAction("...done starting", sslEnabled, port, false));

        return nettyServer;
    }

    private void terminateNetty(int port, int tlsPort, Consumer<String> println) {
        terminateOneNettyServer(server, false, port, println);
        terminateOneNettyServer(tlsServer, true, tlsPort, println);
    }

    private void terminateOneNettyServer(
            Server server, boolean tlsSupport, int port, Consumer<String> println) {
        if (null == server) {
            return;
        }

        try {
            println.accept(nettyAction("Terminating", tlsSupport, port, true));
            server.awaitTermination(TIME_TO_AWAIT_TERMINATION, TimeUnit.SECONDS);
            println.accept(nettyAction("...done terminating", tlsSupport, port, false));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for Netty to terminate on port {}!", port, ie);
        }
    }

    private String nettyAction(String action, boolean tlsSupport, int port, boolean isOpening) {
        return String.format(
                "%s Netty%s on port %d%s",
                action, tlsSupport ? " with TLS support" : "", port, isOpening ? "..." : ".");
    }
}
