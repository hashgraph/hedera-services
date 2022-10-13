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
package com.hedera.services.state.virtual.entities;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.test.utils.SeededPropertySource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OnDiskAccountMigrationTest {
    @CsvSource({"1", "2", "3", "4", "5", "6", "7", "8", "9"})
    @ParameterizedTest
    @SuppressWarnings("java:S5961")
    void allFieldsAreSet(final int testCaseNo) {
        final var source = SeededPropertySource.forSerdeTest(14, testCaseNo);
        final var inMemoryAccount = source.nextAccountState();
        final var onDiskAccount = OnDiskAccount.from(inMemoryAccount);

        // Objects
        assertEquals(inMemoryAccount.memo(), onDiskAccount.getMemo());
        assertEquals(inMemoryAccount.getAlias(), onDiskAccount.getAlias());
        assertEquals(inMemoryAccount.getCryptoAllowances(), onDiskAccount.getHbarAllowances());
        assertEquals(
                inMemoryAccount.getFungibleTokenAllowances(),
                onDiskAccount.getFungibleAllowances());
        assertEquals(
                inMemoryAccount.getApproveForAllNfts(), onDiskAccount.getNftOperatorApprovals());
        assertTrue(JKey.equalUpToDecodability(inMemoryAccount.key(), onDiskAccount.getKey()));
        // Flags
        assertEquals(inMemoryAccount.isDeleted(), onDiskAccount.isDeleted());
        assertEquals(inMemoryAccount.isSmartContract(), onDiskAccount.isContract());
        assertEquals(inMemoryAccount.isDeclineReward(), onDiskAccount.isDeclineReward());
        assertEquals(
                inMemoryAccount.isReceiverSigRequired(), onDiskAccount.isReceiverSigRequired());
        // Ints
        assertEquals(
                inMemoryAccount.getNumContractKvPairs(), onDiskAccount.getNumContractKvPairs());
        assertEquals(
                inMemoryAccount.getMaxAutomaticAssociations(),
                onDiskAccount.getMaxAutoAssociations());
        assertEquals(
                inMemoryAccount.getUsedAutomaticAssociations(),
                onDiskAccount.getUsedAutoAssociations());
        assertEquals(inMemoryAccount.getNumAssociations(), onDiskAccount.getNumAssociations());
        assertEquals(
                inMemoryAccount.getNumPositiveBalances(), onDiskAccount.getNumPositiveBalances());
        assertEquals(inMemoryAccount.getNumTreasuryTitles(), onDiskAccount.getNumTreasuryTitles());
        // Longs
        assertEquals(inMemoryAccount.expiry(), onDiskAccount.getExpiry());
        assertEquals(inMemoryAccount.balance(), onDiskAccount.getHbarBalance());
        assertEquals(inMemoryAccount.autoRenewSecs(), onDiskAccount.getAutoRenewSecs());
        assertEquals(inMemoryAccount.nftsOwned(), onDiskAccount.getNftsOwned());
        assertEquals(inMemoryAccount.number(), onDiskAccount.getAccountNumber());
        assertEquals(inMemoryAccount.getHeadTokenId(), onDiskAccount.getHeadTokenId());
        assertEquals(inMemoryAccount.getHeadNftId(), onDiskAccount.getHeadNftId());
        assertEquals(inMemoryAccount.getHeadNftSerialNum(), onDiskAccount.getHeadNftSerialNum());
        assertEquals(inMemoryAccount.ethereumNonce(), onDiskAccount.getEthereumNonce());
        assertEquals(inMemoryAccount.getStakedToMe(), onDiskAccount.getStakedToMe());
        assertEquals(inMemoryAccount.getStakePeriodStart(), onDiskAccount.getStakePeriodStart());
        assertEquals(inMemoryAccount.getStakedNum(), onDiskAccount.getStakedNum());
        assertEquals(
                inMemoryAccount.getStakeAtStartOfLastRewardedPeriod(),
                onDiskAccount.getStakeAtStartOfLastRewardedPeriod());
        assertEquals(
                inMemoryAccount.getAutoRenewAccount().num(),
                onDiskAccount.getAutoRenewAccountNumber());
        // Complex
        assertEquals(
                inMemoryAccount.getFirstUint256KeyNonZeroBytes(),
                onDiskAccount.getFirstStorageKeyNonZeroBytes());
        assertArrayEquals(inMemoryAccount.getFirstUint256Key(), onDiskAccount.getFirstStorageKey());
    }
}
