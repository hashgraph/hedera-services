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
package com.hedera.services.ledger;

import static com.hedera.services.ledger.properties.AccountProperty.NUM_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_POSITIVE_BALANCES;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_TREASURY_TITLES;
import static com.hedera.services.ledger.properties.AccountProperty.USED_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.hedera.services.state.submerkle.CurrencyAdjustments;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HederaLedgerTokensTest extends BaseHederaLedgerTestHelper {
    @BeforeEach
    void setup() {
        commonSetup();
        setupWithMockLedger();
    }

    @Test
    void getsTokenBalance() {
        final var balance = subject.getTokenBalance(misc, frozenId);

        assertEquals(miscFrozenTokenBalance, balance);
    }

    @Test
    void recognizesAccountWithNonZeroTokenBalances() {
        assertFalse(subject.allTokenBalancesVanish(misc));
    }

    @Test
    void throwsIfSubjectHasNoUsableTokenRelsLedger() {
        subject.setTokenRelsLedger(null);

        assertThrows(IllegalStateException.class, () -> subject.allTokenBalancesVanish(deletable));
    }

    @Test
    void recognizesAccountWithZeroTokenBalances() {
        when(accountsLedger.get(deletable, NUM_POSITIVE_BALANCES)).thenReturn(0);

        assertTrue(subject.allTokenBalancesVanish(deletable));
    }

    @Test
    void adjustsIfValid() {
        givenOkTokenXfers(any(), any(), anyLong());

        final var status = subject.adjustTokenBalance(misc, tokenId, 555);

        verify(tokenStore).adjustBalance(misc, tokenId, 555);
        assertEquals(OK, status);
    }

    @Test
    void injectsLedgerToTokenStore() {
        verify(tokenStore).setAccountsLedger(accountsLedger);
        verify(tokenStore).setHederaLedger(subject);
        verify(creator).setLedger(subject);
    }

    @Test
    void delegatesFreezeOps() {
        subject.freeze(misc, frozenId);
        verify(tokenStore).freeze(misc, frozenId);

        subject.unfreeze(misc, frozenId);
        verify(tokenStore).unfreeze(misc, frozenId);
    }

    @Test
    void delegatesKnowingOps() {
        subject.grantKyc(misc, frozenId);
        verify(tokenStore).grantKyc(misc, frozenId);
    }

    @Test
    void delegatesTokenChangeDrop() {
        given(nftsLedger.isInTransaction()).willReturn(true);

        subject.dropPendingTokenChanges();

        verify(tokenRelsLedger).rollback();
        verify(nftsLedger).rollback();
        verify(accountsLedger)
                .undoChangesOfType(
                        List.of(
                                NUM_POSITIVE_BALANCES,
                                NUM_ASSOCIATIONS,
                                NUM_NFTS_OWNED,
                                USED_AUTOMATIC_ASSOCIATIONS,
                                NUM_TREASURY_TITLES));
        verify(sideEffectsTracker).resetTrackedTokenChanges();
    }

    @Test
    void onlyRollsbackIfTokenRelsLedgerInTxn() {
        given(tokenRelsLedger.isInTransaction()).willReturn(false);

        subject.dropPendingTokenChanges();

        verify(tokenRelsLedger, never()).rollback();
    }

    @Test
    void forwardsTransactionalSemanticsToTokenLedgersIfPresent() {
        final var inOrder = inOrder(tokensLedger, tokenRelsLedger, nftsLedger);
        given(tokenRelsLedger.isInTransaction()).willReturn(true);
        given(tokensLedger.isInTransaction()).willReturn(true);
        given(nftsLedger.isInTransaction()).willReturn(true);
        given(sideEffectsTracker.getNetTrackedHbarChanges()).willReturn(new CurrencyAdjustments());

        subject.begin();
        subject.commit();
        subject.begin();
        subject.rollback();

        inOrder.verify(tokensLedger).begin();
        inOrder.verify(tokenRelsLedger).begin();
        inOrder.verify(nftsLedger).begin();
        inOrder.verify(tokensLedger).isInTransaction();
        inOrder.verify(tokensLedger).commit();
        inOrder.verify(tokenRelsLedger).isInTransaction();
        inOrder.verify(tokenRelsLedger).commit();
        inOrder.verify(nftsLedger).isInTransaction();
        inOrder.verify(nftsLedger).commit();

        inOrder.verify(tokensLedger).begin();
        inOrder.verify(tokenRelsLedger).begin();
        inOrder.verify(nftsLedger).begin();
        inOrder.verify(tokensLedger).isInTransaction();
        inOrder.verify(tokensLedger).rollback();
        inOrder.verify(tokenRelsLedger).isInTransaction();
        inOrder.verify(tokenRelsLedger).rollback();
        inOrder.verify(nftsLedger).isInTransaction();
        inOrder.verify(nftsLedger).rollback();
    }
}
