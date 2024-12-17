/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionResponse;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.pbj.runtime.RpcMethodDefinition;
import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * The requests and responses for different network services.
 */
@SuppressWarnings("java:S6548")
public final class NetworkServiceDefinition implements RpcServiceDefinition {
    /**
     * The global singleton NetworkServiceDefinition INSTANCE.
     */
    public static final NetworkServiceDefinition INSTANCE = new NetworkServiceDefinition();

    private static final Set<RpcMethodDefinition<?, ?>> methods = Set.of(
            new RpcMethodDefinition<>("getVersionInfo", Query.class, Response.class),
            new RpcMethodDefinition<>("getExecutionTime", Query.class, Response.class),
            new RpcMethodDefinition<>("uncheckedSubmit", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("getAccountDetails", Query.class, Response.class));

    private NetworkServiceDefinition() {
        // Just something to keep the Gradle build believing we have a non-transitive
        // "requires" and hence preserving our module-info.class in the compiled JAR
        requireNonNull(CommonUtils.class);
    }

    /**
     * Returns the base path for the Network Service.
     *
     * @return the base path
     */
    @Override
    @NonNull
    public String basePath() {
        return "proto.NetworkService";
    }

    /**
     * Returns the set of rpc methods supported by the network service.
     *
     * @return the methods
     */
    @Override
    @NonNull
    public Set<RpcMethodDefinition<?, ?>> methods() {
        return methods;
    }
}
