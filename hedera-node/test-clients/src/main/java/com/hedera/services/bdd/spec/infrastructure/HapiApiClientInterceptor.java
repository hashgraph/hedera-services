package com.hedera.services.bdd.spec.infrastructure;

import io.grpc.ClientInterceptor;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class HapiApiClientInterceptor {

    HapiApiClients client;
    ChannelStubs stub;
    boolean useTls;

    public HapiApiClientInterceptor(HapiApiClients client, ChannelStubs stub, boolean useTls) {
        this.client = client;
        this.stub = stub;
        this.useTls = useTls;
    }


}
