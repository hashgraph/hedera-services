package com.hedera.services.bdd.spec.infrastructure.interceptor;

import com.hedera.services.bdd.spec.infrastructure.ChannelStubs;
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceResponse;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RetryInterceptor implements ClientInterceptor {
    private final HapiApiClients client;
    private final ChannelStubs stub;
    private final boolean useTls;
    private final AtomicInteger retries;

    private final RetryCallbacks retryCallbacks;

    // A ConcurrentHashMap to store request messages for each ClientCall.
    private final ConcurrentHashMap<Integer, Object> requestMessages;

    private RetryInterceptor(HapiApiClients client, ChannelStubs stub, boolean useTls, int retries, RetryCallbacks retryCallbacks) {
        this.client = client;
        this.stub = stub;
        this.useTls = useTls;
        this.retries = new AtomicInteger(retries);
        this.retryCallbacks = retryCallbacks;
        this.requestMessages = new ConcurrentHashMap<>();
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);
        int callId = System.identityHashCode(call);
        System.out.printf("RetryInterceptor: call id: %d\n", callId);

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(call) {

            @Override
            public void sendMessage(ReqT message) {
                System.out.printf("called sendMessage: %s\n", message);
                requestMessages.put(callId, message); // Store the request message
                super.sendMessage(message);
            }

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                System.out.println("SimpleForwardingClientCall: Calling start");

                ClientCall.Listener<RespT> newListener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        System.out.printf("called onClose - close client - status code: %s\n", status.getCode());
                        if (retries.getAndDecrement() > 0 && status.getCode() == Status.Code.UNAVAILABLE) {
                            System.out.printf("retrying call: %d\n", callId);
                            // Handle reconnection logic here if it's a GOAWAY issue
                            // This may involve creating a new channel and retrying the operation
                            final var newStub = client.recreateChannelAndStub(stub, useTls);
                            final var oldChannel = stub.channel();
                            System.out.printf("oldChannel shut down? %s\n", oldChannel.isShutdown());
                            final var newChannel = newStub.channel();

                            // Retry the call on the new channel.
                            retryCall(method, callOptions, newChannel, this, headers, callId, oldChannel);

                        } else {
                            System.out.println("closing SimpleForwardingClientCallListener");
                            super.onClose(status, trailers);
                        }
                    }

                    @Override
                    public void onMessage(RespT message) {
                        String className = message.getClass().getName();
                        System.out.printf("newListener.onMessage: Class [%s], Content [%s]\n", className, message.toString());
                        super.onMessage(message);
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
            Metadata originalHeaders,
            int callId,
            ManagedChannel oldChannel) {

        if (this.retryCallbacks != null) {
            retryCallbacks.onRetry(method, callOptions, newChannel, originalListener, originalHeaders);
//            if (!retryCallbacks.onRetry(method, callOptions, newChannel, originalListener, originalHeaders)) {
//                // The callback is allowed to cancel the execution of retry
//                // when false is returned.
//                return;
//            }
        }

        ClientCall<ReqT, RespT> retryCall = newChannel.newCall(method, callOptions);
        retryCall.start(new ClientCall.Listener<RespT>() {

            @Override
            public void onHeaders(Metadata headers) {
                System.out.printf("called onHeaders: %s\n", headers);
                originalListener.onHeaders(headers);
                super.onHeaders(headers);
            }

            @Override
            public void onMessage(RespT message) {
                System.out.println("called onMessage");
                originalListener.onMessage(message);
                super.onMessage(message);
            }

            @Override
            public void onClose(Status status, Metadata trailers) {
                System.out.println("called onClose");
                originalListener.onClose(status, trailers);
                super.onClose(status, trailers);
            }

            @Override
            public void onReady() {
                System.out.println("called onReady");
                originalListener.onReady();
                super.onReady();
            }
        }, originalHeaders);

        // You need to resend the request message.
        // This requires storing the original request message or having a way to recreate it.
        ReqT lastRequest = (ReqT) requestMessages.get(callId);

//        System.out.printf("lastRequest: %s", lastRequest);
        if (lastRequest != null) {
            System.out.println("Sending request to server again");
            retryCall.sendMessage(lastRequest);
            System.out.println("half close");
            retryCall.halfClose(); // Indicate the end of messages for this RPC
            System.out.println("did half close");
        } else {
            System.out.println("last request is null");
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder implements HapiApiClientsInterceptor {
        private int retries;
        private RetryCallbacks retryCallbacks;

        public Builder(){}

        public Builder setRetries(int retries) {
            this.retries = retries;
            return this;
        }

        public Builder setRetryCallbacks(RetryCallbacks retryCallbacks) {
            this.retryCallbacks = retryCallbacks;
            return this;
        }

        public RetryInterceptor Build(@NonNull HapiApiClients client,
                                      @NonNull ChannelStubs stub,
                                      boolean useTls) {
            return new RetryInterceptor(client, stub, useTls, this.retries, this.retryCallbacks);
        }
    }
}
