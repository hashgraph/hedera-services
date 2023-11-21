package com.hedera.services.bdd.spec.infrastructure.interceptor;

import com.hedera.services.bdd.spec.infrastructure.ChannelStubs;
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.ClientInterceptor;

public interface HapiApiClientsInterceptor {
    public ClientInterceptor Build(@NonNull HapiApiClients client,
                                   @NonNull ChannelStubs stub,
                                   boolean useTls);
}
