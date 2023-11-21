package com.hedera.services.bdd.spec.infrastructure.interceptor;

import io.grpc.*;

public interface RetryCallbacks {
    public <ReqT, RespT> boolean onRetry(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel newChannel,
            ClientCall.Listener<RespT> originalListener,
            Metadata originalHeaders
    );
}
