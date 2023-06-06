/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.node.app.service.contract.impl.exec.v038.SystemAwareAddressChecks;
import java.util.Map;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class SystemAwareAddressChecksTest {
    private SystemAwareAddressChecks subject = new SystemAwareAddressChecks(Map.of());

    @Test
    void onlyBelow750IsSystem() {
        assertTrue(subject.isSystemAccount(Address.fromHexString(Integer.toHexString(749))));
        assertTrue(subject.isSystemAccount(Address.fromHexString(Integer.toHexString(750))));
        assertFalse(subject.isSystemAccount(Address.fromHexString(Integer.toHexString(751))));
    }
}
