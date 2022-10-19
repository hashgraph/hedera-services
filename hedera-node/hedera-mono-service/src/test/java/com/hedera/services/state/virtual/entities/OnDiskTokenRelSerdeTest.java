package com.hedera.services.state.virtual.entities;

import com.hedera.test.serde.VirtualValueDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class OnDiskTokenRelSerdeTest extends VirtualValueDataTest<OnDiskTokenRel> {
    @Override
    protected Class<OnDiskTokenRel> getType() {
        return OnDiskTokenRel.class;
    }

    @Override
    protected OnDiskTokenRel getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.nextOnDiskTokenRel();
    }
}