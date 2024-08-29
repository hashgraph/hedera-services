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

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionResponse;
import com.hedera.pbj.runtime.RpcMethodDefinition;
import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * The request and responses for freeze service.
 */
@SuppressWarnings("java:S6548")
public final class FreezeServiceDefinition implements RpcServiceDefinition {
    /**
     * The global singleton FreezeServiceDefinition INSTANCE.
     */
    public static final FreezeServiceDefinition INSTANCE = new FreezeServiceDefinition();

    private static final Set<RpcMethodDefinition<?, ?>> methods =
            Set.of(new RpcMethodDefinition<>("freeze", Transaction.class, TransactionResponse.class));

    private FreezeServiceDefinition() {
        // Forbid instantiation
    }

    /**
     * Returns the the base path for the freeze service
     *
     * @return the base path
     */
    @Override
    @NonNull
    public String basePath() {
        return "proto.FreezeService";
    }

    /**
     * Returns the set of rpc methods supported by the freeze service
     *
     * @return the methods
     */
    @Override
    @NonNull
    public Set<RpcMethodDefinition<?, ?>> methods() {
        return methods;
    }
}
