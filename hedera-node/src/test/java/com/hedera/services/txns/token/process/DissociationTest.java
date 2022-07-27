/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.token.process;

import static com.hedera.services.state.enums.TokenType.FUNGIBLE_COMMON;
import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DissociationTest {
    private static final long tokenExpiry = 1_234_567L;
    private static final Id accountId = new Id(1, 2, 3);
    private static final Id tokenId = new Id(2, 3, 4);
    private static final Id treasuryId = new Id(3, 4, 5);
    private static final Id veryAncientTreasuryId = new Id(0, 0, 3);
    private static final Account account = new Account(accountId);
    private static final Account treasury = new Account(treasuryId);
    private static final Account ancientTreasury = new Account(veryAncientTreasuryId);
    private final Token token = new Token(tokenId);
    private final TokenRelationship dissociatingAccountRel = new TokenRelationship(token, account);
    private final TokenRelationship dissociatedTokenTreasuryRel =
            new TokenRelationship(token, treasury);
    private final TokenRelationship ancientTokenTreasuryRel =
            new TokenRelationship(token, ancientTreasury);

    {
        token.setTreasury(treasury);
        token.setExpiry(tokenExpiry);
        token.setType(FUNGIBLE_COMMON);
        dissociatingAccountRel.markAsPersisted();
    }

    @Mock private TypedTokenStore tokenStore;
    @Mock private OptionValidator validator;

    @Test
    void loadsExpectedRelsForExtantToken() {
        given(tokenStore.loadPossiblyDeletedOrAutoRemovedToken(tokenId)).willReturn(token);
        given(tokenStore.loadTokenRelationship(token, account)).willReturn(dissociatingAccountRel);
        given(tokenStore.loadPossiblyMissingTokenRelationship(token, treasury))
                .willReturn(dissociatedTokenTreasuryRel);

        final var subject = Dissociation.loadFrom(tokenStore, account, tokenId);

        assertSame(dissociatingAccountRel, subject.dissociatingAccountRel());
        assertSame(dissociatedTokenTreasuryRel, subject.dissociatedTokenTreasuryRel());
        assertSame(tokenId, subject.dissociatedTokenId());
        assertSame(accountId, subject.dissociatingAccountId());
    }

    @Test
    void loadsExpectedRelsForAutoRemovedToken() {
        token.markAutoRemoved();
        given(tokenStore.loadPossiblyDeletedOrAutoRemovedToken(tokenId)).willReturn(token);
        given(tokenStore.loadTokenRelationship(token, account)).willReturn(dissociatingAccountRel);

        final var subject = Dissociation.loadFrom(tokenStore, account, tokenId);

        verify(tokenStore, never()).loadTokenRelationship(token, treasury);
        assertSame(dissociatingAccountRel, subject.dissociatingAccountRel());
        assertNull(subject.dissociatedTokenTreasuryRel());
        assertSame(tokenId, subject.dissociatedTokenId());
        assertSame(accountId, subject.dissociatingAccountId());
    }

    @Test
    void loadsExpectedRelsForTokenDissociatedFromTreasury() {
        given(tokenStore.loadPossiblyDeletedOrAutoRemovedToken(tokenId)).willReturn(token);
        given(tokenStore.loadTokenRelationship(token, account)).willReturn(dissociatingAccountRel);
        given(tokenStore.loadPossiblyMissingTokenRelationship(token, treasury)).willReturn(null);

        final var subject = Dissociation.loadFrom(tokenStore, account, tokenId);

        assertSame(dissociatingAccountRel, subject.dissociatingAccountRel());
        assertSame(null, subject.dissociatedTokenTreasuryRel());
        assertSame(tokenId, subject.dissociatedTokenId());
        assertSame(accountId, subject.dissociatingAccountId());
    }

    @Test
    void requiresUpdateDoneBeforeRevealingRels() {
        final var list = new ArrayList<TokenRelationship>();
        final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

        assertThrows(IllegalStateException.class, () -> subject.addUpdatedModelRelsTo(list));
    }

    @Test
    void processesAutoRemovedTokenAsExpected() {
        final var subject = new Dissociation(dissociatingAccountRel, null);
        final List<TokenRelationship> changed = new ArrayList<>();
        token.markAutoRemoved();

        subject.updateModelRelsSubjectTo(validator);
        subject.addUpdatedModelRelsTo(changed);

        assertEquals(1, changed.size());
        assertSame(dissociatingAccountRel, changed.get(0));
        assertTrue(dissociatingAccountRel.isDestroyed());
    }

    @Test
    void rejectsDissociatingTokenTreasury() {
        token.setTreasury(account);

        final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

        assertFailsWith(() -> subject.updateModelRelsSubjectTo(validator), ACCOUNT_IS_TREASURY);
    }

    @Test
    void rejectsDissociatingFrozenAccount() {
        dissociatingAccountRel.setFrozen(true);

        final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

        assertFailsWith(
                () -> subject.updateModelRelsSubjectTo(validator), ACCOUNT_FROZEN_FOR_TOKEN);
    }

    @Test
    void normalCaseOnlyUpdatesDissociatingRel() {
        final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);
        final List<TokenRelationship> accum = new ArrayList<>();

        subject.updateModelRelsSubjectTo(validator);
        subject.addUpdatedModelRelsTo(accum);

        assertEquals(1, accum.size());
        assertSame(dissociatingAccountRel, accum.get(0));
        assertTrue(dissociatingAccountRel.isDestroyed());
    }

    @Test
    void requiresZeroBalanceWhenDissociatingFromActiveToken() {
        final long balance = 1_234L;
        dissociatingAccountRel.initBalance(balance);
        given(validator.isAfterConsensusSecond(tokenExpiry)).willReturn(true);

        final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

        assertFailsWith(
                () -> subject.updateModelRelsSubjectTo(validator),
                TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
    }

    @Test
    void cannotAutoRevertOwnershipToTreasuryEvenForExpired() {
        final long balance = 1L;
        dissociatingAccountRel.initBalance(balance);
        token.setType(NON_FUNGIBLE_UNIQUE);

        final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

        assertFailsWith(() -> subject.updateModelRelsSubjectTo(validator), ACCOUNT_STILL_OWNS_NFTS);
    }

    @Test
    void autoTransfersBalanceBackToTreasuryForExpiredToken() {
        final long balance = 1_234L;
        dissociatingAccountRel.initBalance(balance);
        dissociatedTokenTreasuryRel.initBalance(balance);
        final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);
        final List<TokenRelationship> accum = new ArrayList<>();

        subject.updateModelRelsSubjectTo(validator);
        subject.addUpdatedModelRelsTo(accum);

        assertEquals(2, accum.size());
        assertEquals(token, subject.dissociatingToken());
        assertEquals(account, subject.dissociatingAccount());
        assertEquals(dissociatingAccountRel.getBalanceChange(), -balance);
        assertSame(dissociatingAccountRel, accum.get(0));
        assertTrue(dissociatingAccountRel.isDestroyed());
        assertSame(dissociatedTokenTreasuryRel, accum.get(1));
        assertEquals(dissociatedTokenTreasuryRel.getBalanceChange(), +balance);
    }

    @Test
    void autoTransfersBalanceBackToTreasuryRespectingIdOrdering() {
        final long balance = 1_234L;
        dissociatingAccountRel.initBalance(balance);
        ancientTokenTreasuryRel.initBalance(balance);
        final var subject = new Dissociation(dissociatingAccountRel, ancientTokenTreasuryRel);
        final List<TokenRelationship> accum = new ArrayList<>();

        subject.updateModelRelsSubjectTo(validator);
        subject.addUpdatedModelRelsTo(accum);

        assertEquals(2, accum.size());
        assertEquals(dissociatingAccountRel.getBalanceChange(), -balance);
        assertSame(dissociatingAccountRel, accum.get(1));
        assertTrue(dissociatingAccountRel.isDestroyed());
        assertSame(ancientTokenTreasuryRel, accum.get(0));
        assertEquals(ancientTokenTreasuryRel.getBalanceChange(), +balance);
    }

    @Test
    void oksDissociatedDeletedTokenTreasury() {
        final long balance = 1_234L;
        dissociatingAccountRel.initBalance(balance);
        token.setTreasury(account);
        token.setIsDeleted(true);
        final List<TokenRelationship> accum = new ArrayList<>();

        final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

        subject.updateModelRelsSubjectTo(validator);
        subject.addUpdatedModelRelsTo(accum);

        assertEquals(1, accum.size());
        assertSame(dissociatingAccountRel, accum.get(0));
        assertEquals(-balance, dissociatingAccountRel.getBalanceChange());
    }

    @Test
    void oksDissociatedDeletedUniqueTokenTreasury() {
        final long balance = 1_234L;
        account.setOwnedNfts(balance);
        dissociatingAccountRel.initBalance(balance);
        token.setTreasury(account);
        token.setIsDeleted(true);
        token.setType(NON_FUNGIBLE_UNIQUE);
        final List<TokenRelationship> accum = new ArrayList<>();

        final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

        subject.updateModelRelsSubjectTo(validator);
        subject.addUpdatedModelRelsTo(accum);

        assertEquals(1, accum.size());
        assertSame(dissociatingAccountRel, accum.get(0));
        assertEquals(-balance, dissociatingAccountRel.getBalanceChange());
        assertEquals(0, account.getOwnedNfts());
    }

    @Test
    void stillDissociatesFromDeletedTokenWithBalanceChangeEvenAfterTreasuryGone() {
        final long balance = 1_234L;
        account.setOwnedNfts(balance);
        dissociatingAccountRel.initBalance(balance);
        token.setTreasury(account);
        token.setIsDeleted(true);
        token.setType(FUNGIBLE_COMMON);
        final List<TokenRelationship> accum = new ArrayList<>();

        final var subject = new Dissociation(dissociatingAccountRel, null);

        subject.updateModelRelsSubjectTo(validator);
        subject.addUpdatedModelRelsTo(accum);

        assertEquals(1, accum.size());
        assertSame(dissociatingAccountRel, accum.get(0));
        assertEquals(-balance, dissociatingAccountRel.getBalanceChange());
    }

    @Test
    void toStringWorks() {
        final var desired =
                "Dissociation{dissociatingAccountId=1.2.3, dissociatedTokenId=2.3.4,"
                        + " dissociatedTokenTreasuryId=3.4.5,"
                        + " expiredTokenTreasuryReceivedBalance=false}";

        final var subject = new Dissociation(dissociatingAccountRel, dissociatedTokenTreasuryRel);

        assertEquals(desired, subject.toString());
    }

    private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
        final var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }
}
