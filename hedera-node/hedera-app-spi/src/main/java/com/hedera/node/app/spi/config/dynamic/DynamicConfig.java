package com.hedera.node.app.spi.config.dynamic;

import com.hedera.node.app.spi.config.dynamic.api.DynamicData;
import com.hedera.node.app.spi.config.dynamic.api.DynamicProperty;
import com.hedera.node.app.spi.config.dynamic.api.Property;

@DynamicData
public record DynamicConfig(@DynamicProperty Property<Integer> count) {

}
