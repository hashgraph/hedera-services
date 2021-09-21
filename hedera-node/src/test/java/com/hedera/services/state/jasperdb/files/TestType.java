package com.hedera.services.state.jasperdb.files;

enum TestType {
    fixed(new ExampleFixedSizeDataSerializer()),
    variable(new ExampleVariableSizeDataSerializer());
    public final DataItemSerializer<long[]> dataItemSerializer;

    TestType(DataItemSerializer<long[]> dataItemSerializer) {
        this.dataItemSerializer = dataItemSerializer;
    }
}
