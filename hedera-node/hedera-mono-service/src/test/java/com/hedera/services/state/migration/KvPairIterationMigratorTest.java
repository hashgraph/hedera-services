/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.migration;

import static org.apache.tuweni.units.bigints.UInt256.ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.store.contracts.SizeLimitedStorage;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KvPairIterationMigratorTest {
    @Mock private SizeLimitedStorage.IterableStorageUpserter storageUpserter;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private VirtualMap<ContractKey, IterableContractValue> iterableContractStorage;
    @Mock private VirtualRootNode<ContractKey, IterableContractValue> rootNode;

    private KvPairIterationMigrator subject;

    @BeforeEach
    void setUp() {
        subject =
                new KvPairIterationMigrator(1, accounts, storageUpserter, iterableContractStorage);
    }

    @Test
    void migratesAsExpected() throws InterruptedException {
        givenMutableContract(contractNum, contractSomeKvPairs);
        givenMutableContract(otherContractNum, contractNoKvPairs);
        given(iterableContractStorage.copy()).willReturn(iterableContractStorage);
        given(
                        storageUpserter.upsertMapping(
                                tailKey, iterableTailValue, null, null, iterableContractStorage))
                .willReturn(tailKey);
        given(
                        storageUpserter.upsertMapping(
                                rootKey, iterableRootValue, tailKey, null, iterableContractStorage))
                .willReturn(rootKey);
        given(iterableContractStorage.getRight()).willReturn(rootNode);

        subject.accept(Pair.of(tailKey, tailValue));
        subject.accept(Pair.of(zeroKey, zeroValue));
        subject.accept(Pair.of(rootKey, rootValue));
        subject.accept(Pair.of(otherZeroKey, zeroValue));
        subject.accept(Pair.of(missingKey, tailValue));
        subject.finish();

        verify(iterableContractStorage, times(2)).copy();
        verify(iterableContractStorage, times(2)).release();
        assertEquals(2, contractSomeKvPairs.getNumContractKvPairs());
        assertEquals(0, contractNoKvPairs.getNumContractKvPairs());
        assertSame(iterableContractStorage, subject.getMigratedStorage());
    }

    private void givenMutableContract(final long num, final MerkleAccount contract) {
        final var key = EntityNum.fromLong(num);
        given(accounts.containsKey(key)).willReturn(true);
        given(accounts.getForModify(key)).willReturn(contract);
    }

    private static final long contractNum = 666;
    private static final long otherContractNum = 777;
    private static final long missingContractNum = 888;
    private static final AccountID contractId =
            AccountID.newBuilder().setAccountNum(contractNum).build();
    private static final AccountID missingId =
            AccountID.newBuilder().setAccountNum(missingContractNum).build();
    private static final UInt256 rootEvmKey = UInt256.fromHexString("0xaabbcc");
    private static final UInt256 zeroEvmKey = UInt256.fromHexString("0xbbccdd");
    private static final UInt256 tailEvmKey = UInt256.fromHexString("0xffeedd");
    private static final ContractKey zeroKey = ContractKey.from(contractNum, zeroEvmKey);
    private static final ContractKey otherZeroKey = ContractKey.from(otherContractNum, zeroEvmKey);
    private static final ContractKey rootKey = ContractKey.from(contractId, rootEvmKey);
    private static final ContractKey missingKey = ContractKey.from(missingId, rootEvmKey);
    private static final ContractKey tailKey = ContractKey.from(contractId, tailEvmKey);
    public static final ContractValue zeroValue = ContractValue.from(ZERO);
    private static final UInt256 rootEvmValue =
            UInt256.fromHexString(
                    "0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563");
    private static final UInt256 tailEvmValue =
            UInt256.fromHexString(
                    "0x210aeca1542b62a2a60345a122326fc24ba6bc15424002f6362f13160ef3e563");
    public static final ContractValue rootValue = ContractValue.from(rootEvmValue);
    public static final IterableContractValue iterableRootValue =
            IterableContractValue.from(rootEvmValue);
    public static final ContractValue tailValue = ContractValue.from(tailEvmValue);
    public static final IterableContractValue iterableTailValue =
            IterableContractValue.from(tailEvmValue);

    private final MerkleAccount contractSomeKvPairs =
            MerkleAccountFactory.newContract()
                    .balance(123)
                    .number(EntityNum.fromLong(contractNum))
                    .numKvPairs(3)
                    .get();
    private final MerkleAccount contractNoKvPairs =
            MerkleAccountFactory.newContract()
                    .balance(321)
                    .number(EntityNum.fromLong(otherContractNum))
                    .numKvPairs(1)
                    .get();
}
