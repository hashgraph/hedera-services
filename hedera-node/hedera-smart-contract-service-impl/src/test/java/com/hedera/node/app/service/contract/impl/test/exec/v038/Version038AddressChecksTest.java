/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.v038;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.contract.impl.exec.v038.Version038AddressChecks;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import java.util.Map;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class Version038AddressChecksTest {
    private Version038AddressChecks subject = new Version038AddressChecks(Map.of());

    @Test
    void onlyBelow750IsSystem() {
        assertTrue(subject.isSystemAccount(Address.fromHexString(Integer.toHexString(749))));
        assertTrue(subject.isSystemAccount(Address.fromHexString(Integer.toHexString(750))));
        assertFalse(subject.isSystemAccount(Address.fromHexString(Integer.toHexString(751))));
        assertFalse(subject.isSystemAccount(TestHelpers.EIP_1014_ADDRESS));
    }

    @Test
    void onlyAbove1000IsUserAccount() {
        assertTrue(subject.isNonUserAccount(Address.fromHexString(Integer.toHexString(1000))));
        assertFalse(subject.isNonUserAccount(Address.fromHexString(Integer.toHexString(1001))));
        assertFalse(subject.isSystemAccount(TestHelpers.EIP_1014_ADDRESS));
    }
}
