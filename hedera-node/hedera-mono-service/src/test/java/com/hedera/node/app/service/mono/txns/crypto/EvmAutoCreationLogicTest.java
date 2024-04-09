/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.txns.crypto;

import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmAutoCreationLogicTest {

    @Mock
    private UsageLimits usageLimits;

    @Mock
    private StateView currentView;

    @Mock
    private EntityIdSource ids;

    @Mock
    private EntityCreator creator;

    @Mock
    private TransactionContext txnCtx;

    @Mock
    private ContractAliases contractAliases;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private GlobalDynamicProperties properties;

    private EvmAutoCreationLogic subject;

    @BeforeEach
    void setUp() {
        subject = new EvmAutoCreationLogic(
                usageLimits, syntheticTxnFactory, creator, ids, () -> currentView, txnCtx, properties, contractAliases);

        subject.setFeeCalculator(feeCalculator);
    }

    private static final EntityNum num = EntityNum.fromLong(1234L);
    private static final byte[] rawNonMirrorAddress = unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbb");
    private static final Address nonMirrorAddress = Address.wrap(Bytes.wrap(rawNonMirrorAddress));
    private static final Address mirrorAddress = num.toEvmAddress();

    @Test
    void tracksAliasAsExpected() {
        final var newId = EntityIdUtils.accountIdFromEvmAddress(mirrorAddress);
        final var alias = ByteStringUtils.wrapUnsafely(nonMirrorAddress.toArrayUnsafe());

        subject.trackAlias(alias, newId);

        verify(contractAliases).link(nonMirrorAddress, EntityIdUtils.asTypedEvmAddress(newId));
    }

    @Test
    void tracksAliasThrowsWhenAliasIsNotAnEvmAddress() {
        final var bytes = new byte[EVM_ADDRESS_SIZE + 1];
        final var alias = ByteStringUtils.wrapUnsafely(bytes);
        final var entityNum = EntityIdUtils.accountIdFromEvmAddress(mirrorAddress);
        assertThrows(UnsupportedOperationException.class, () -> subject.trackAlias(alias, entityNum));

        final var bytes2 = new byte[EVM_ADDRESS_SIZE - 1];
        final var alias2 = ByteStringUtils.wrapUnsafely(bytes2);
        final var entityNum2 = EntityIdUtils.accountIdFromEvmAddress(mirrorAddress);
        assertThrows(UnsupportedOperationException.class, () -> subject.trackAlias(alias2, entityNum2));
    }

    @Test
    void tracksAliasThrowsWhenAliasIsMirror() {
        final var alias = ByteStringUtils.wrapUnsafely(mirrorAddress.toArray());
        final var entityNum = EntityIdUtils.accountIdFromEvmAddress(mirrorAddress);
        assertThrows(IllegalArgumentException.class, () -> subject.trackAlias(alias, entityNum));
    }

    @Test
    void reclaimPendingAliasesThrows() {
        assertThrows(IllegalStateException.class, () -> subject.reclaimPendingAliases());
    }
}
