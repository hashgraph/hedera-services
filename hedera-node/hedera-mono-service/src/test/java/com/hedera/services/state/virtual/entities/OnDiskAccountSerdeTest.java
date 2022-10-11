package com.hedera.services.state.virtual.entities;

import com.hedera.test.serde.VirtualValueDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class OnDiskAccountSerdeTest extends VirtualValueDataTest<OnDiskAccount> {
    public static final int NUM_ON_DISK_ACCOUNT_TEST_CASES = 2 * MIN_TEST_CASES_PER_VERSION;

    @Override
    protected int getNumTestCasesFor(final int version) {
        return NUM_ON_DISK_ACCOUNT_TEST_CASES;
    }

    @Override
    protected Class<OnDiskAccount> getType() {
        return OnDiskAccount.class;
    }

    @Override
    protected OnDiskAccount getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.nextOnDiskAccount();
    }
}