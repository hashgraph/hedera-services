/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts.operation;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaCreate2OperationTest {
    private static final long baseGas = 100L;
    private static final Bytes salt = Bytes.fromHexString("0x2a");
    private static final Bytes oneOffsetStackItem = Bytes.of(10);
    private static final Bytes twoOffsetStackItem = Bytes.of(20);
    private static final MutableBytes initcode = MutableBytes.of((byte) 0xaa);
    private Address recipientAddr = Address.fromHexString("0x0102030405060708090a0b0c0d0e0f1011121314");

    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private MessageFrame frame;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private HederaStackedWorldStateUpdater stackedUpdater;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private EntityCreator creator;

    @Mock
    private RecordsHistorian recordsHistorian;

    private HederaCreate2Operation subject;

    @BeforeEach
    void setup() {
        subject = new HederaCreate2Operation(
                gasCalculator, creator, syntheticTxnFactory, recordsHistorian, dynamicProperties);
    }
}
