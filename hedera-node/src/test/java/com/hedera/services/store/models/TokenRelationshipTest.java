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
package com.hedera.services.store.models;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenRelationshipTest {
    private final Id tokenId = new Id(0, 0, 1234);
    private final Id accountId = new Id(1, 0, 4321);
    private final long balance = 1_234L;
    private final JKey kycKey = TxnHandlingScenario.TOKEN_KYC_KT.asJKeyUnchecked();
    private final JKey freezeKey = TxnHandlingScenario.TOKEN_FREEZE_KT.asJKeyUnchecked();

    private Token token;
    private Account account;

    private TokenRelationship subject;

    @BeforeEach
    void setUp() {
        token = new Token(tokenId);
        account = new Account(accountId);
        int associatedTokensCount = 3;
        account.setNumAssociations(associatedTokensCount);

        subject = new TokenRelationship(token, account);
        subject.initBalance(balance);
    }

    @Test
    void ofRecordInterestIfFungibleBalanceChanges() {
        token.setType(TokenType.FUNGIBLE_COMMON);

        subject.setBalance(balance - 1);

        assertTrue(subject.hasChangesForRecord());
    }

    @Test
    void notOrdinarilyOfRecordInterestIfNonFungibleBalanceChanges() {
        token.setType(TokenType.NON_FUNGIBLE_UNIQUE);

        subject.setBalance(balance - 1);

        assertFalse(subject.hasChangesForRecord());
    }

    @Test
    void ordinarilyOfRecordInterestIfNonFungibleBalanceChangesForDeletedToken() {
        token.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        token.setIsDeleted(true);

        subject.setBalance(balance - 1);

        assertTrue(subject.hasChangesForRecord());
    }

    @Test
    void asAssociationWorks() {
        final var expected = new FcTokenAssociation(1234, 4321);

        assertEquals(expected, subject.asAutoAssociation());
    }

    @Test
    void toStringAsExpected() {
        // given:
        final var desired =
                "TokenRelationship{notYetPersisted=true, account=Account{id=1.0.4321, expiry=0,"
                    + " balance=0, deleted=false, ownedNfts=0, alreadyUsedAutoAssociations=0,"
                    + " maxAutoAssociations=0, alias=, cryptoAllowances=null,"
                    + " fungibleTokenAllowances=null, approveForAllNfts=null, numAssociations=3,"
                    + " numPositiveBalances=0, ethereumNonce=0}, token=Token{id=0.0.1234,"
                    + " type=null, deleted=false, autoRemoved=false, treasury=null,"
                    + " autoRenewAccount=null, kycKey=<N/A>, freezeKey=<N/A>,"
                    + " frozenByDefault=false, supplyKey=<N/A>, currentSerialNumber=0,"
                    + " pauseKey=<N/A>, paused=false}, balance=1234, balanceChange=0, frozen=false,"
                    + " kycGranted=false, isAutomaticAssociation=false}";

        // expect:
        assertEquals(desired, subject.toString());
    }

    @Test
    void equalsWorks() {
        assertEquals(subject, subject);
        assertNotEquals(subject, freezeKey);
    }

    @Test
    void automaticAssociationSetterWorks() {
        subject.setAutomaticAssociation(false);
        assertFalse(subject.isAutomaticAssociation());

        subject.setAutomaticAssociation(true);
        assertTrue(subject.isAutomaticAssociation());
    }

    @Test
    void cannotChangeBalanceIfFrozenForToken() {
        // given:
        token.setFreezeKey(freezeKey);
        subject.setFrozen(true);

        assertFailsWith(() -> subject.setBalance(balance + 1), ACCOUNT_FROZEN_FOR_TOKEN);
    }

    @Test
    void canChangeBalanceIfFrozenForDeletedToken() {
        token.setFreezeKey(freezeKey);
        token.setIsDeleted(true);

        subject.setFrozen(true);

        subject.setBalance(0);
        assertEquals(-balance, subject.getBalanceChange());
    }

    @Test
    void canChangeBalanceIfUnfrozenForToken() {
        // given:
        token.setFreezeKey(freezeKey);
        subject.setFrozen(false);

        // when:
        subject.setBalance(balance + 1);

        // then:
        assertEquals(1, subject.getBalanceChange());
    }

    @Test
    void canChangeBalanceIfNoFreezeKey() {
        // given:
        subject.setFrozen(true);

        // when:
        subject.setBalance(balance + 1);

        // then:
        assertEquals(1, subject.getBalanceChange());
    }

    @Test
    void cannotChangeBalanceIfKycNotGranted() {
        // given:
        token.setKycKey(kycKey);
        subject.setKycGranted(false);

        // verify
        assertFailsWith(() -> subject.setBalance(balance + 1), ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN);
    }

    @Test
    void canChangeBalanceIfKycGranted() {
        // given:
        token.setKycKey(kycKey);
        subject.setKycGranted(true);

        // when:
        subject.setBalance(balance + 1);

        // then:
        assertEquals(1, subject.getBalanceChange());
    }

    @Test
    void canChangeBalanceIfNoKycKey() {
        // given:
        subject.setKycGranted(false);

        // when:
        subject.setBalance(balance + 1);

        // then:
        assertEquals(1, subject.getBalanceChange());
    }

    @Test
    void updateFreezeWorksIfFeezeKeyIsPresent() {
        // given:
        subject.setFrozen(false);
        token.setFreezeKey(freezeKey);

        // when:
        subject.changeFrozenState(true);

        // then:
        assertTrue(subject.isFrozen());
    }

    @Test
    void updateFreezeFailsAsExpectedIfFreezeKeyIsNotPresent() {
        // given:
        subject.setFrozen(false);
        token.setFreezeKey(null);

        // verify
        assertFailsWith(() -> subject.changeFrozenState(true), TOKEN_HAS_NO_FREEZE_KEY);
    }

    @Test
    void givesCorrectRepresentation() {
        subject.getToken().setType(TokenType.NON_FUNGIBLE_UNIQUE);
        assertTrue(subject.hasUniqueRepresentation());

        subject.getToken().setType(TokenType.FUNGIBLE_COMMON);
        assertTrue(subject.hasCommonRepresentation());
    }

    @Test
    void testHashCode() {
        var rel = new TokenRelationship(token, account);
        rel.initBalance(balance);
        assertEquals(rel.hashCode(), subject.hashCode());
    }

    @Test
    void updateKycWorksIfKycKeyIsPresent() {
        // given:
        subject.setKycGranted(false);
        token.setKycKey(kycKey);

        // when:
        subject.changeKycState(true);

        // then:
        assertTrue(subject.isKycGranted());
    }

    @Test
    void updateKycFailsAsExpectedIfFreezeKeyIsNotPresent() {
        // given:
        subject.setKycGranted(false);
        token.setKycKey(null);

        // verify
        assertFailsWith(() -> subject.changeKycState(true), TOKEN_HAS_NO_KYC_KEY);
    }

    private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
        var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }
}
