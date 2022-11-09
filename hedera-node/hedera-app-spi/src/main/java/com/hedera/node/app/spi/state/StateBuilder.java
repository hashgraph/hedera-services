package com.hedera.node.app.spi.state;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;

public interface StateBuilder {

    <K, V extends MerkleNode & Keyed<K>> InMemoryBuilder<K, V> inMemory(String stateKey);
    <K extends VirtualKey<? super K>, V extends VirtualValue> OnDiskBuilder<K, V> onDisk(String stateKey, String label);

    interface InMemoryBuilder<K, V extends MerkleNode & Keyed<K>> {
        State<K, V> build();
    }

    interface OnDiskBuilder<K extends VirtualKey<? super K>, V extends VirtualValue> {
        OnDiskBuilder<K, V> keySerializer(KeySerializer<K> serializer);
        OnDiskBuilder<K, V> valueSerializer(VirtualLeafRecordSerializer<K, V> leafRecordSerializer);
        State<K, V> build();
    }
}
