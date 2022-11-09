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

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;

public interface StateBuilder {

    <K, V extends MerkleNode & Keyed<K>> InMemoryBuilder<K, V> inMemory(String stateKey);

    <K extends VirtualKey<? super K>, V extends VirtualValue> OnDiskBuilder<K, V> onDisk(
            String stateKey, String label);

    interface InMemoryBuilder<K, V extends MerkleNode & Keyed<K>> {
        State<K, V> build();
    }

    interface OnDiskBuilder<K extends VirtualKey<? super K>, V extends VirtualValue> {
        OnDiskBuilder<K, V> keySerializer(KeySerializer<K> serializer);

        OnDiskBuilder<K, V> valueSerializer(VirtualLeafRecordSerializer<K, V> leafRecordSerializer);

        State<K, V> build();
    }
}
