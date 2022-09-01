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
package com.hedera.services.state.expiry.removal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountGCTest {
    @Mock private AliasManager aliasManager;
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private TreasuryReturns treasuryReturns;
    @Mock private BackingStore<AccountID, MerkleAccount> backingAccounts;
    @Mock private ExpiryThrottle expiryThrottle;

    private AccountGC subject;

    @BeforeEach
    void setUp() {
        subject =
                new AccountGC(
                        aliasManager,
                        expiryThrottle,
                        sigImpactHistorian,
                        treasuryReturns,
                        backingAccounts);
    }

    @Test
    void happyPathWithEverythingFinishedAndAvailable() {
        given(expiryThrottle.allow(AccountGC.ACCOUNT_REMOVAL_WORK)).willReturn(true);
        given(treasuryReturns.returnNftsFrom(expiredAccount))
                .willReturn(NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS);
        given(treasuryReturns.returnFungibleUnitsFrom(expiredAccount))
                .willReturn(FungibleTreasuryReturns.FINISHED_NOOP_FUNGIBLE_RETURNS);
        given(aliasManager.forgetAlias(expiredAccount.getAlias())).willReturn(true);

        final var expectedOutcome =
                new CryptoGcOutcome(
                        FungibleTreasuryReturns.FINISHED_NOOP_FUNGIBLE_RETURNS,
                        NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                        true);

        final var outcome = subject.expireBestEffort(num, expiredAccount);

        verify(backingAccounts).remove(num.toGrpcAccountId());
        verify(sigImpactHistorian).markEntityChanged(num.longValue());
        verify(aliasManager).forgetEvmAddress(expiredAccount.getAlias());
        verify(sigImpactHistorian).markAliasChanged(expiredAccount.getAlias());

        assertEquals(expectedOutcome, outcome);
    }

    @Test
    void pathWithEverythingFinishedButNotAvailable() {
        given(treasuryReturns.returnNftsFrom(expiredAccount))
                .willReturn(NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS);
        given(treasuryReturns.returnFungibleUnitsFrom(expiredAccount))
                .willReturn(FungibleTreasuryReturns.FINISHED_NOOP_FUNGIBLE_RETURNS);

        final var expectedOutcome =
                new CryptoGcOutcome(
                        FungibleTreasuryReturns.FINISHED_NOOP_FUNGIBLE_RETURNS,
                        NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                        false);

        final var outcome = subject.expireBestEffort(num, expiredAccount);

        verifyNoInteractions(backingAccounts, sigImpactHistorian, aliasManager);

        assertEquals(expectedOutcome, outcome);
    }

    @Test
    void pathWithEverythingNftsFinishedButNotUnits() {
        given(treasuryReturns.returnNftsFrom(expiredAccount))
                .willReturn(NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS);
        given(treasuryReturns.returnFungibleUnitsFrom(expiredAccount))
                .willReturn(FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS);

        final var expectedOutcome =
                new CryptoGcOutcome(
                        FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS,
                        NonFungibleTreasuryReturns.FINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                        false);

        final var outcome = subject.expireBestEffort(num, expiredAccount);

        verifyNoInteractions(backingAccounts, sigImpactHistorian, aliasManager);

        assertEquals(expectedOutcome, outcome);
    }

    @Test
    void pathWithNftsNotFinished() {
        given(treasuryReturns.returnNftsFrom(expiredAccount))
                .willReturn(NonFungibleTreasuryReturns.UNFINISHED_NOOP_NON_FUNGIBLE_RETURNS);

        final var expectedOutcome =
                new CryptoGcOutcome(
                        FungibleTreasuryReturns.UNFINISHED_NOOP_FUNGIBLE_RETURNS,
                        NonFungibleTreasuryReturns.UNFINISHED_NOOP_NON_FUNGIBLE_RETURNS,
                        false);

        final var outcome = subject.expireBestEffort(num, expiredAccount);

        verifyNoInteractions(backingAccounts, sigImpactHistorian, aliasManager);

        assertEquals(expectedOutcome, outcome);
    }

    private final EntityNum num = EntityNum.fromLong(1234);
    private final MerkleAccount expiredAccount = new MerkleAccount();

    {
        expiredAccount.setAlias(ByteString.copyFromUtf8("SOMETHING"));
    }
}
