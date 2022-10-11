package com.hedera.services.state.virtual.entities;

import com.hedera.test.serde.VirtualValueDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class OnDiskAccountSerdeTest extends VirtualValueDataTest<OnDiskAccount> {
    @Override
    protected Class<OnDiskAccount> getType() {
        return OnDiskAccount.class;
    }

    @Override
    protected OnDiskAccount getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.nextOnDiskAccount();
    }
}