/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.merkle.disk;

import com.hedera.hapi.streams.v7.StateChange;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;

public interface BlockObserver {
    void recordRoundStateChanges(
            @NonNull final StateChangesSink sink, @NonNull final Round round, @NonNull final Runnable fn);

    void recordEventStateChanges(
            @NonNull final StateChangesSink sink,
            @NonNull final ConsensusEvent platformEvent,
            @NonNull final Runnable fn);

    void recordSystemTransactionStateChanges(
            @NonNull final StateChangesSink sink,
            @NonNull final ConsensusTransaction platformTxn,
            @NonNull final Runnable fn);

    void recordUserTransactionStateChanges(
            @NonNull final StateChangesSink sink,
            @NonNull final ConsensusTransaction platformTxn,
            @NonNull final Runnable fn);

    void recordUserChildTransactionStateChanges(@NonNull final Runnable fn);

    void addStateChange(@NonNull final StateChange stateChange);

    <K, V> void mapUpdateChange(@NonNull final String stateKey, @NonNull final K key, @NonNull final V value);

    <V> void queuePushChange(@NonNull final String stateKey, @NonNull final V value);

    void queuePopChange(@NonNull final String stateKey);

    <V> void singletonUpdateChange(@NonNull final String stateKey, @NonNull final V value);

    void singletonDeleteChange(@NonNull final String stateKey);
}
