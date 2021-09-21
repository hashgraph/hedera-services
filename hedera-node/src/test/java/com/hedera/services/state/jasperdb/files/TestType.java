package com.hedera.services.state.jasperdb.files;

import com.hedera.services.state.jasperdb.ExampleFixedSizeLongKey;
import com.hedera.services.state.jasperdb.ExampleVariableSizeLongKey;
import com.hedera.services.state.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualLongKey;

enum TestType {
    fixed(new ExampleFixedSizeDataSerializer(), new ExampleFixedSizeLongKey.Serializer()),
    variable(new ExampleVariableSizeDataSerializer(), new ExampleVariableSizeLongKey.Serializer());
    public final DataItemSerializer<long[]> dataItemSerializer;
    public final KeySerializer<? extends VirtualLongKey> keySerializer;

    TestType(DataItemSerializer<long[]> dataItemSerializer, KeySerializer<? extends VirtualLongKey> keySerializer) {
        this.dataItemSerializer = dataItemSerializer;
        this.keySerializer = keySerializer;
    }
}
