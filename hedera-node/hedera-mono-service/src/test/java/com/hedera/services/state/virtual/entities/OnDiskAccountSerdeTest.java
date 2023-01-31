/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.virtual.entities;

import com.hedera.test.serde.VirtualValueDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class OnDiskAccountSerdeTest extends VirtualValueDataTest<OnDiskAccount> {
    public static final int NUM_ON_DISK_ACCOUNT_TEST_CASES = 3 * MIN_TEST_CASES_PER_VERSION;

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
