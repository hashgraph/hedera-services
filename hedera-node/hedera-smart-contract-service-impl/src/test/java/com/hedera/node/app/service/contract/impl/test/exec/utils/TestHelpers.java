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

package com.hedera.node.app.service.contract.impl.test.exec.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Objects;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.operation.Operation;

public class TestHelpers {
    public static long REQUIRED_GAS = 123L;
    public static Address SYSTEM_ADDRESS =
            Address.fromHexString(BigInteger.valueOf(750).toString(16));
    public static Address PRECOMPILE_ADDRESS = Address.fromHexString("0x167");
    public static Address NON_SYSTEM_LONG_ZERO_ADDRESS = Address.fromHexString("0x1234576890");
    public static Address EIP_1014_ADDRESS = Address.fromHexString("0x89abcdef89abcdef89abcdef89abcdef89abcdef");

    public static void assertSameResult(
            final Operation.OperationResult expected, final Operation.OperationResult actual) {
        assertEquals(expected.getHaltReason(), actual.getHaltReason());
        assertEquals(expected.getGasCost(), actual.getGasCost());
    }

    public static boolean isSameResult(
            final Operation.OperationResult expected,
            final Operation.OperationResult actual) {
        return Objects.equals(expected.getHaltReason(), actual.getHaltReason()) &&
                expected.getGasCost() == actual.getGasCost();
    }
}
