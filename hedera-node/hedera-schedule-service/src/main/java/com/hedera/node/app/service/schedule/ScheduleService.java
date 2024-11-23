/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.RpcService;
import com.hedera.node.app.spi.RpcServiceFactory;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.pbj.runtime.RpcServiceDefinition;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/schedule_service.proto">Schedule
 * Service</a>.
 */
public interface ScheduleService extends RpcService {

    String NAME = "ScheduleService";

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }

    /**
     * Returns the RPC definitions for the service.
     *
     * @return the RPC definitions
     */
    @NonNull
    @Override
    default Set<RpcServiceDefinition> rpcDefinitions() {
        return Set.of(ScheduleServiceDefinition.INSTANCE);
    }

    /**
     * Returns the concrete implementation instance of the service.
     *
     * @return the implementation instance
     */
    @NonNull
    static ScheduleService getInstance() {
        return RpcServiceFactory.loadService(ScheduleService.class, ServiceLoader.load(ScheduleService.class));
    }

    /**
     * An executable transaction with the verifier to use for child signature verifications.
     */
    record ExecutableTxn<T extends StreamBuilder>(
            @NonNull TransactionBody body,
            @NonNull AccountID payerId,
            @NonNull Predicate<Key> keyVerifier,
            @NonNull Instant nbf,
            @NonNull Class<T> builderType,
            @NonNull Consumer<T> builderSpec) {}

    /**
     * Given a [start, end) interval and a supplier of a StoreFactory that can be used in the returned
     * iterator's remove() implementation to get a StoreFactory to purge a successfully executed txn,
     *
     * @return an iterator over all ExecutableTxn this service wants to execute in the interval.
     */
    default Iterator<ExecutableTxn<?>> iterTxnsForInterval(
            Instant start, Instant end, Supplier<StoreFactory> cleanupStoreFactory, State state) {
        // Default implementation returns an empty iterator
        return Collections.emptyIterator();
    }
}
