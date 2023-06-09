/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.grpc;

import static com.hedera.node.app.service.mono.utils.SleepingPause.SLEEPING_PAUSE;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.utils.Pause;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.BindableService;
import io.helidon.common.configurable.Resource;
import io.helidon.common.configurable.ResourceException;
import io.helidon.config.Config;
import io.helidon.config.MapConfigSource;
import io.helidon.grpc.core.GrpcTlsDescriptor;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class HelidonGrpcServerManager implements GrpcServerManager {
    private static final Logger log = LogManager.getLogger(HelidonGrpcServerManager.class);
    private static final long TIME_TO_AWAIT_TERMINATION = 5;

    private final Set<BindableService> bindableServices;
    private final Consumer<Thread> shutdownHook;
    private GrpcServer server;
    private GrpcServer tlsServer;
    private final NodeLocalProperties nodeProperties;

    @Inject
    public HelidonGrpcServerManager(
            @NonNull final Set<BindableService> bindableServices,
            @NonNull final Consumer<Thread> shutdownHook,
            @NonNull final NodeLocalProperties nodeProperties) {
        this.bindableServices = requireNonNull(bindableServices);
        this.shutdownHook = requireNonNull(shutdownHook);
        this.nodeProperties = requireNonNull(nodeProperties);
    }

    @Override
    public void start(int port, int tlsPort, @NonNull Consumer<String> println) {
        // Add a shutdown hook to the JVM, such that the grpc server is shutdown when the JVM is shutdown
        this.shutdownHook.accept(new Thread(() -> {
            terminateOneServer(server, false, port, println);
            terminateOneServer(tlsServer, true, tlsPort, println);
        }));

        try {
            server = startOneServer(false, port, println, SLEEPING_PAUSE);
            tlsServer = startOneServer(true, tlsPort, println, SLEEPING_PAUSE);
        } catch (ResourceException e) {
            tlsServer = null;
            String message = logMessage("Could not start", true, tlsPort, false);
            log.warn("{} ({}).", message, e.getMessage());
            println.accept(message);
        }
    }

    GrpcServer startOneServer(boolean sslEnabled, int port, Consumer<String> println, Pause pause) {
        println.accept(logMessage("Starting", sslEnabled, port, true));

        // Setup the GRPC Routing, such that all grpc services are registered
        final var grpcRoutingBuilder = GrpcRouting.builder();
        bindableServices.forEach(grpcRoutingBuilder::register);

        // Create the GRPC Server
        log.info("Configuring a Helidon gRPC server on port {} (TLS {})", port, (sslEnabled ? "ON" : "OFF"));

        final Config initialConfig =
                Config.builder(getMapSource(nodeProperties)).build();
        final var configBuilder =
                GrpcServerConfiguration.builder().config(initialConfig).port(port);
        /* Note:  We would like to set all of the following, but Helidon simply doesn't support it.
                 keepAliveTime(nodeProperties.nettyProdKeepAliveTime(), TimeUnit.SECONDS)
                 permitKeepAliveTime(nodeProperties.nettyProdKeepAliveTime(), TimeUnit.SECONDS)
                 keepAliveTimeout(nodeProperties.nettyProdKeepAliveTimeout(), TimeUnit.SECONDS)
                 maxConnectionAge(nodeProperties.nettyMaxConnectionAge(), TimeUnit.SECONDS)
                 maxConnectionAgeGrace(nodeProperties.nettyMaxConnectionAgeGrace(), TimeUnit.SECONDS)
                 maxConnectionIdle(nodeProperties.nettyMaxConnectionIdle(), TimeUnit.SECONDS)
                 maxConcurrentCallsPerConnection(nodeProperties.nettyMaxConcurrentCalls())
                 flowControlWindow(nodeProperties.nettyFlowControlWindow())
        */
        // Note: SSL enabled defaults to true, jdkSSL defaults to false.  jdkSSL, in particular,
        //       should remain false unless specifically required to use the JDK internal TLS
        //       implementation over the native TLS implementation.
        if (sslEnabled) {
            configBuilder.tlsConfig(GrpcTlsDescriptor.builder()
                    .enabled(sslEnabled)
                    .jdkSSL(false)
                    .tlsCert(Resource.create(Path.of(nodeProperties.nettyTlsCrtPath())))
                    .tlsKey(Resource.create(Path.of(nodeProperties.nettyTlsKeyPath())))
                    .build());
        }

        final var grpcServer = GrpcServer.create(configBuilder.build(), grpcRoutingBuilder);

        // Start the grpc server. Note that we have to do some retry logic because our default port is
        // 50211, 50212, which are both in the ephemeral port range, and may very well be in use right
        // now. Of course this doesn't fix that, but it does give us a chance. What we really should do,
        // is stop using ports above 10K.
        final var startRetries = nodeProperties.nettyStartRetries();
        final var startRetryIntervalMs = nodeProperties.nettyStartRetryIntervalMs();
        var retryNo = 1;
        final var n = Math.max(0, startRetries);
        for (; retryNo <= n; retryNo++) {
            try {
                grpcServer.start();
                break;
            } catch (Exception e) {
                final var summaryMsg = logMessage("Still trying to start", sslEnabled, port, true);
                log.warn("(Attempts={}) {}", retryNo, summaryMsg, e);
                pause.forMs(startRetryIntervalMs);
            }
        }
        if (retryNo == n + 1) {
            grpcServer.start();
        }

        println.accept(logMessage("...done starting", sslEnabled, port, false));

        return grpcServer;
    }

    private MapConfigSource getMapSource(@NonNull final NodeLocalProperties nodeProperties) {
        Objects.requireNonNull(nodeProperties, "Should not be possible; null passed for node properties.");
        final Map<String, String> nodePropertiesMap = new HashMap<>(2);
        nodePropertiesMap.put("native", "true");
        nodePropertiesMap.put("port", Integer.toString(nodeProperties.port()));
        return MapConfigSource.builder().map(nodePropertiesMap).build();
    }

    private void terminateOneServer(GrpcServer server, boolean tlsSupport, int port, Consumer<String> println) {
        if (server == null) {
            return;
        }

        try {
            println.accept(logMessage("Terminating", tlsSupport, port, true));
            server.shutdown().toCompletableFuture().get(TIME_TO_AWAIT_TERMINATION, TimeUnit.SECONDS);
            println.accept(logMessage("...done terminating", tlsSupport, port, false));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for Helidon gRPC to terminate on port {}!", port, ie);
        } catch (Exception e) {
            log.warn("Exception while waiting for Helidon gRPC to terminate on port {}!", port, e);
        }
    }

    private String logMessage(String action, boolean tlsSupport, int port, boolean isOpening) {
        return String.format(
                "%s Helidon gRPC%s on port %d%s",
                action, tlsSupport ? " with TLS support" : "", port, isOpening ? "..." : ".");
    }
}
