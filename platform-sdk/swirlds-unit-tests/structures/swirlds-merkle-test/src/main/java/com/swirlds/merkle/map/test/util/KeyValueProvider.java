/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.map.test.util;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.common.test.dummy.Key;
import com.swirlds.common.test.dummy.Value;
import com.swirlds.merkle.map.test.dummy.FCQValue;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public enum KeyValueProvider {
    KEY_VALUE(
            MapMutatorSet.ValueType.SIMPLE,
            () -> new Key(1, 1, 1),
            () -> Value.newBuilder()
                    .setReceiveThresholdValue(12345L)
                    .setSendThresholdvalue(54321L)
                    .setBalance(98766)
                    .setReceiveSignatureRequired(true)
                    .build(),
            (index) -> new Key(index, index, index),
            Value::buildRandomValue),

    KEY_FCQ(
            MapMutatorSet.ValueType.FCQ,
            () -> new Key(1, 1, 1),
            FCQValue::buildDefault,
            (index) -> new Key(index, index, index),
            FCQValue::buildRandom);

    private final MapMutatorSet mapMutatorSet;

    private final Supplier<Key> defaultKey;

    private final Supplier<MerkleNode> defaultValue;

    private final Function<Integer, Key> keyFactory;

    private final Supplier<MerkleNode> randomValueFactory;

    @SuppressWarnings("unchecked")
    <V extends MerkleNode> KeyValueProvider(
            final MapMutatorSet.ValueType valueType,
            final Supplier<Key> defaultKey,
            final Supplier<V> defaultValue,
            final Function<Integer, Key> keyFactory,
            final Supplier<V> randomValueFactory) {
        this.mapMutatorSet = new MapMutatorSet(valueType);
        this.defaultKey = defaultKey;
        this.defaultValue = (Supplier<MerkleNode>) defaultValue;
        this.keyFactory = keyFactory;
        this.randomValueFactory = (Supplier<MerkleNode>) randomValueFactory;
    }

    public <V extends MerkleNode> void insertIntoMap(final int startIndex, final int endIndex, final Map<Key, V> fcm) {
        mapMutatorSet.insertIntoMap(startIndex, endIndex, fcm);
    }

    public <V extends MerkleNode & Keyed<Key>> void updateMap(final Map<Key, V> fcm, final int percentage) {
        mapMutatorSet.updateValueInMap(fcm, percentage);
    }

    public <V extends MerkleNode & Keyed<Key>> void deleteFromMap(final Map<Key, V> fcm, final int percentage) {
        mapMutatorSet.deleteFromMap(fcm, percentage);
    }

    public Key getDefaultKey() {
        return this.defaultKey.get();
    }

    @SuppressWarnings("unchecked")
    public <V extends MerkleNode> V getDefaultValue() {
        return (V) this.defaultValue.get();
    }

    public Key getKeyBasedOnIndex(final int index) {
        return this.keyFactory.apply(index);
    }

    @SuppressWarnings("unchecked")
    public <V extends MerkleNode> V buildRandomValue() {
        return (V) this.randomValueFactory.get();
    }
}
