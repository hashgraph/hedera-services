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

package com.hedera.node.app.service.contract.impl.test.exec.gas;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCalculator;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class CustomGasCalculatorTest {
    private final CustomGasCalculator subject = new CustomGasCalculator();

    @Test
    void txnIntrinsicCostContractCreate() {
        assertEquals(
                21_000L + // base TX cost
                        32_000L, // contract creation base cost
                subject.transactionIntrinsicGasCost(Bytes.EMPTY, true));
    }

    @Test
    void txnIntrinsicCostNonContractCreate() {
        assertEquals(
                21_000L, // base TX cost
                subject.transactionIntrinsicGasCost(Bytes.EMPTY, false));
    }

    @Test
    void codeDepositCostIsZero() {
        assertEquals(0, subject.codeDepositGasCost(Integer.MAX_VALUE));
    }

    @Test
    void transactionIntrinsicGasCost() {
        assertEquals(
                4 * 2 + // zero byte cost
                        16 * 3 + // non-zero byte cost
                        21_000L, // base TX cost
                subject.transactionIntrinsicGasCost(Bytes.of(0, 1, 2, 3, 0), false));
        assertEquals(
                4 * 3 + // zero byte cost
                        16 * 2 + // non-zero byte cost
                        21_000L + // base TX cost
                        32_000L, // contract creation base cost
                subject.transactionIntrinsicGasCost(Bytes.of(0, 1, 0, 3, 0), true));
    }
}
