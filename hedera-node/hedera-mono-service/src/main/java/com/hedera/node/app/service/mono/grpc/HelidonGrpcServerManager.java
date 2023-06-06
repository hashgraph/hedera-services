package com.hedera.node.app.service.mono.grpc;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.BindableService;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import static java.util.Objects.requireNonNull;

@Singleton
public class HelidonGrpcServerManager implements GrpcServerManager {
    private final Set<BindableService> bindableServices;

    @Inject
    public HelidonGrpcServerManager(@NonNull final Set<BindableService> bindableServices) {
        this.bindableServices = requireNonNull(bindableServices);
    }

    @Override
    public void start(int port, int tlsPort, Consumer<String> println) {
        final var grpcRoutingBuilder = GrpcRouting.builder();
        bindableServices.forEach(grpcRoutingBuilder::register);

        final var grpcServer = GrpcServer.create(
                GrpcServerConfiguration.builder().port(port),
                grpcRoutingBuilder);

        grpcServer.start();
    }
}
