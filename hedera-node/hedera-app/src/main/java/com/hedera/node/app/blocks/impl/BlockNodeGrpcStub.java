package com.hedera.node.app.blocks.impl;

import com.hedera.hapi.block.protoc.BlockStreamServiceGrpc;
import com.hedera.hapi.block.protoc.PublishStreamRequest;
import com.hedera.hapi.block.protoc.PublishStreamResponse;
import io.grpc.stub.StreamObserver;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class BlockNodeGrpcStub extends BlockStreamServiceGrpc.BlockStreamServiceImplBase {

    private long latency;
    private Server server;

    @Override
    public StreamObserver<PublishStreamRequest> publishBlockStream(
            StreamObserver<PublishStreamResponse> responseObserver) {
        return new StreamObserver<PublishStreamRequest>() {
            @Override
            public void onNext(PublishStreamRequest publishStreamRequest) {
                long requestTimeMillis = TimeUnit.SECONDS.toMillis(publishStreamRequest.getRequestTimestamp().getSeconds())
                        + TimeUnit.NANOSECONDS.toMillis(publishStreamRequest.getRequestTimestamp().getNanos());
                latency = System.currentTimeMillis() - requestTimeMillis;
            }

            @Override
            public void onError(Throwable throwable) {
                // Do nothing on error
            }

            @Override
            public void onCompleted() {
                // Do nothing on completion
            }
        };
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 9090;
        final Server server = ServerBuilder.forPort(port)
                .addService(new BlockNodeGrpcStub())
                .build()
                .start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            server.shutdown();
            System.err.println("*** server shut down");
        }));

        server.awaitTermination();
    }

    public Server startServer(int port) throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(this)
                .build()
                .start();

        return server;
    }

    public void stopServer() {
        if (server != null) {
            server.shutdown();
        }
    }

    public long getLatency() {
        return latency;
    }
}