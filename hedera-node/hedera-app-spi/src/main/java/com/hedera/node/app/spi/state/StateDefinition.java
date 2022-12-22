/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.state;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * @param stateKey The "state key" that uniquely identifies this {@link ReadableKVState} within the
 *     {@link Schema} which are scoped to the service implementation. The key is therefore not
 *     globally unique, only unique within the service implementation itself.
 * @param keySerdes The {@link Serdes} to use for parsing and writing keys in the registered state
 * @param valueSerdes The {@link Serdes} to use for parsing and writing values in the registered
 *     state
 * @param maxKeysHint A hint as to the maximum number of keys to be stored in this state. This value
 *     CANNOT CHANGE from one schema version to another. If it is changed, you will need to do a
 *     long-form migration to a new state.
 * @param onDisk Whether to store this state on disk
 * @param <K> The type of key
 * @param <V> The type of value
 */
public record StateDefinition<K extends Comparable<K>, V>(
        @NonNull String stateKey,
        @NonNull Serdes<K> keySerdes,
        @NonNull Serdes<V> valueSerdes,
        int maxKeysHint,
        boolean onDisk) {}
