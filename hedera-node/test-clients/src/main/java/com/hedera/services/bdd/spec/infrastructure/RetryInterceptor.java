package com.hedera.services.bdd.spec.infrastructure;

import io.grpc.*;

import java.util.concurrent.atomic.AtomicInteger;

public class RetryInterceptor implements ClientInterceptor {

    private final HapiApiClients client;
    private final ChannelStubs stub;
    private final boolean useTls;

    private final AtomicInteger retries;

    public RetryInterceptor(HapiApiClients client, ChannelStubs stub, boolean useTls, int retries) {
        this.client = client;
        this.stub = stub;
        this.useTls = useTls;
        this.retries = new AtomicInteger(retries);
        System.out.printf("retries %d\n", retries);
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                ClientCall.Listener<RespT> newListener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        if (retries.getAndDecrement() > 0 && status.getCode() == Status.Code.UNAVAILABLE) {
                            // Handle reconnection logic here if it's a GOAWAY issue
                            // This may involve creating a new channel and retrying the operation
                            final var newStub = client.recreateChannelAndStub(stub, useTls);
                            final var newChannel = newStub.channel();

                            // Retry the call on the new channel.
                            retryCall(method, callOptions, newChannel, this, headers);

                        }
                        super.onClose(status, trailers);
                    }
                };
                super.start(newListener, headers);
            }
        };
    }
    private <ReqT, RespT> void retryCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel newChannel,
            ClientCall.Listener<RespT> originalListener,
            Metadata originalHeaders) {

        System.out.println("retrying call");

//        io.grpc.stub.ClientCalls.blockingUnaryCall(
//                newChannel, method, callOptions, request);
        ClientCall<ReqT, RespT> retryCall = newChannel.newCall(method, callOptions);
        retryCall.start(new ClientCall.Listener<RespT>() {
            @Override
            public void onMessage(RespT message) {
                originalListener.onMessage(message);
            }

            @Override
            public void onClose(Status status, Metadata trailers) {
                originalListener.onClose(status, trailers);
            }

            // Implement other listener methods as needed
        }, originalHeaders);

        // You need to resend the request message.
        // This requires storing the original request message or having a way to recreate it.
    }
}
