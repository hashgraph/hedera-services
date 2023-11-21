package com.hedera.services.bdd.spec.infrastructure.interceptor;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;

public class RetryingClientCallListener<ReqT, RespT> extends ClientCall.Listener<RespT> {
    private final ClientCall.Listener<RespT> delegate;
    private final ClientCall<ReqT, RespT> call;
    private final ReqT request;

    private final RetryCallbacks retryCallbacks;

    public RetryingClientCallListener(ClientCall.Listener<RespT> delegate,
                                      ClientCall<ReqT, RespT> call,
                                      ReqT request,
                                      RetryCallbacks retryCallbacks) {
        this.delegate = delegate;
        this.call = call;
        this.request = request;
        this.retryCallbacks = retryCallbacks;
    }

    @Override
    public void onMessage(RespT message) {
        delegate.onMessage(message);
    }

    @Override
    public void onHeaders(Metadata headers) {
        delegate.onHeaders(headers);
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
        if (shouldRetry(status)) {
            // Logic to retry the call
            retryCall();
        } else {
            delegate.onClose(status, trailers);
        }
    }

    @Override
    public void onReady() {
        // When the call is ready, send the message
        if (call.isReady()) {
            call.sendMessage(request);
            call.halfClose(); // Close after sending the message
        }
        delegate.onReady();
    }

    private boolean shouldRetry(Status status) {
        // Implement your retry condition logic here
        return status.getCode() == Status.Code.UNAVAILABLE;
    }

    private void retryCall() {
        // Implement the retry logic here
        // This might involve creating a new call and listener
//        if (this.retryCallbacks != null) {
//            if (!this.retryCallbacks.onRetry(method, callOptions, newChannel, originalListener, originalHeaders)) {
//                // The callback is allowed to cancel the execution of retry
//                // when false is returned.
//                return;
//            }
//        }
//
//        ReqT lastRequest = (ReqT) requestMessages.get(callId);

    }
}