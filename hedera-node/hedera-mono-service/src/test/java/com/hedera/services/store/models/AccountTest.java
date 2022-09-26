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

import static com.hedera.services.state.merkle.internals.BitPackUtils.buildAutomaticAssociationMetaData;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountTest {
    private static final byte[] mockCreate2Addr = unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb");
    private final long miscAccountNum = 12345;
    private final long treasuryNum = 11111;
    private final long firstAssocTokenNum = 666;
    private final long secondAssocTokenNum = 777;
    private final long thirdAssocTokenNum = 555;
    private final Id subjectId = new Id(0, 0, miscAccountNum);
    private final Id treasuryId = new Id(0, 0, treasuryNum);
    private final long ownedNfts = 5;
    private final int numAssociations = 3;
    private final int numPositiveBalances = 2;
    private final int alreadyUsedAutoAssociations = 123;
    private final int maxAutoAssociations = 1234;
    private final int autoAssociationMetadata =
            buildAutomaticAssociationMetaData(maxAutoAssociations, alreadyUsedAutoAssociations);
    private final TokenRelationship firstRel = new TokenRelationship(null, null);
    private final TokenRelationship secondRel = new TokenRelationship(null, null);
    private final Token firstToken = new Token(new Id(0, 0, firstAssocTokenNum));
    private final Token secondToken = new Token(new Id(0, 0, secondAssocTokenNum));
    private final Token thirdToken = new Token(new Id(0, 0, thirdAssocTokenNum));
    private final Account treasuryAccount = new Account(treasuryId);

    private Account subject;
    private OptionValidator validator;
    private TypedTokenStore tokenStore;
    private GlobalDynamicProperties dynamicProperties;

    @BeforeEach
    void setUp() {
        subject = new Account(subjectId);
        subject.setAutoAssociationMetadata(autoAssociationMetadata);
        subject.setOwnedNfts(ownedNfts);
        subject.setNumAssociations(numAssociations);
        subject.setNumPositiveBalances(numPositiveBalances);

        firstToken.setTreasury(treasuryAccount);
        secondToken.setTreasury(treasuryAccount);
        thirdToken.setTreasury(treasuryAccount);

        validator = mock(ContextOptionValidator.class);
        tokenStore = mock(TypedTokenStore.class);
        dynamicProperties = mock(GlobalDynamicProperties.class);
    }

    @Test
    void cannotSetNegativeCounters() {
        subject.setNumPositiveBalances(-1);
        subject.setNumAssociations(-2);

        assertEquals(0, subject.getNumPositiveBalances());
        assertEquals(0, subject.getNumAssociations());
    }

    @Test
    void canManipulateTreasuryTitles() {
        subject.setNumTreasuryTitles(3);
        assertEquals(3, subject.getNumTreasuryTitles());
        subject.incrementNumTreasuryTitles();
        assertEquals(4, subject.getNumTreasuryTitles());
        subject.decrementNumTreasuryTitles();
        assertEquals(3, subject.getNumTreasuryTitles());

        subject.setNumTreasuryTitles(0);
        assertFailsWith(() -> subject.decrementNumTreasuryTitles(), FAIL_INVALID);
    }

    @Test
    void canonicalAddressIsMirrorWithEmptyAlias() {
        assertEquals(EntityNum.fromModel(subjectId).toEvmAddress(), subject.canonicalAddress());
    }

    @Test
    void canonicalAddressIs20ByteAliasIfPresent() {
        subject.setAlias(ByteString.copyFrom(mockCreate2Addr));
        assertEquals(Address.wrap(Bytes.wrap(mockCreate2Addr)), subject.canonicalAddress());
    }

    @Test
    void canonicalAddressIsEVMAddressIfCorrectAlias() {
        // default truffle address #0
        subject.setAlias(
                ByteString.copyFrom(
                        Hex.decode(
                                "3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d")));
        assertEquals(
                Address.wrap(Bytes.fromHexString("627306090abaB3A6e1400e9345bC60c78a8BEf57")),
                subject.canonicalAddress());
    }

    @Test
    void invalidCanonicalAddresses() {
        Address untranslatedAddress =
                Address.wrap(Bytes.fromHexString("0000000000000000000000000000000000003039"));

        // bogus alias
        subject.setAlias(ByteString.copyFromUtf8("This alias is invalid"));
        assertEquals(untranslatedAddress, subject.canonicalAddress());

        // incorrect starting bytes for ECDSA
        subject.setAlias(
                ByteString.copyFrom(
                        Hex.decode(
                                "ffff03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d")));
        assertEquals(untranslatedAddress, subject.canonicalAddress());

        // incorrect ECDSA key
        subject.setAlias(
                ByteString.copyFrom(
                        Hex.decode(
                                "3a21ffaf80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d")));
        assertEquals(untranslatedAddress, subject.canonicalAddress());
    }

    @Test
    void objectContractWorks() {
        final var TEST_KEY = TOKEN_ADMIN_KT.asJKeyUnchecked();
        final var TEST_LONG_VALUE = 1L;
        final var TEST_BOOLEAN_VALUE = true;

        subject.setExpiry(TEST_LONG_VALUE);
        assertEquals(TEST_LONG_VALUE, subject.getExpiry());

        subject.setDeleted(TEST_BOOLEAN_VALUE);
        assertTrue(subject.isDeleted());

        subject.setSmartContract(TEST_BOOLEAN_VALUE);
        assertTrue(subject.isSmartContract());

        subject.setReceiverSigRequired(TEST_BOOLEAN_VALUE);
        assertTrue(subject.isReceiverSigRequired());

        subject.initBalance(TEST_LONG_VALUE);
        assertEquals(TEST_LONG_VALUE, subject.getBalance());

        subject.setAutoRenewSecs(TEST_LONG_VALUE);
        assertEquals(TEST_LONG_VALUE, subject.getAutoRenewSecs());

        subject.setKey(TEST_KEY);
        assertEquals(TEST_KEY, subject.getKey());

        subject.setMemo("Test memo");
        assertEquals("Test memo", subject.getMemo());

        subject.setProxy(Id.DEFAULT);
        assertEquals(Id.DEFAULT, subject.getProxy());

        assertTrue(subject.getMutableCryptoAllowances().isEmpty());
        assertTrue(subject.getMutableFungibleTokenAllowances().isEmpty());
        assertTrue(subject.getMutableApprovedForAllNfts().isEmpty());
    }

    @Test
    void toStringAsExpected() {
        final var desired =
                "Account{id=0.0.12345, expiry=0, balance=0, deleted=false, ownedNfts=5,"
                        + " alreadyUsedAutoAssociations=123, maxAutoAssociations=1234, alias=,"
                        + " cryptoAllowances=null, fungibleTokenAllowances=null,"
                        + " approveForAllNfts=null, numAssociations=3, numPositiveBalances=2, "
                        + "ethereumNonce=0}";

        // expect:
        assertEquals(desired, subject.toString());
    }

    @Test
    void dissociationOnLastAssociatedTokenWorks() {
        // setup:
        final var alreadyAssocTokenId = new Id(0, 0, firstAssocTokenNum);
        final var dissociationRel = mock(Dissociation.class);
        final var tokenRel = mock(TokenRelationship.class);

        given(dissociationRel.dissociatingAccountId()).willReturn(subjectId);
        given(dissociationRel.dissociatedTokenId()).willReturn(alreadyAssocTokenId);
        given(dissociationRel.dissociatingAccountRel()).willReturn(tokenRel);
        given(dissociationRel.dissociatingToken()).willReturn(firstToken);
        given(tokenRel.isAutomaticAssociation()).willReturn(true);
        given(tokenRel.getBalanceChange()).willReturn(-1L);
        given(tokenStore.loadPossiblyDeletedOrAutoRemovedToken(any())).willReturn(secondToken);
        given(tokenStore.loadTokenRelationship(any(), any())).willReturn(secondRel);

        // when:
        subject.dissociateUsing(List.of(dissociationRel), validator);

        // then:
        verify(dissociationRel).updateModelRelsSubjectTo(validator);
        assertEquals(numPositiveBalances - 1, subject.getNumPositiveBalances());
        assertEquals(numAssociations - 1, subject.getNumAssociations());
        assertEquals(
                alreadyUsedAutoAssociations - 1, subject.getAlreadyUsedAutomaticAssociations());
    }

    @Test
    void dissociationWorks() {
        // setup:
        final var alreadyAssocTokenId = new Id(0, 0, secondAssocTokenNum);
        final var dissociationRel = mock(Dissociation.class);
        final var tokenRel = mock(TokenRelationship.class);

        given(dissociationRel.dissociatingAccountId()).willReturn(subjectId);
        given(dissociationRel.dissociatedTokenId()).willReturn(alreadyAssocTokenId);
        given(dissociationRel.dissociatingAccountRel()).willReturn(tokenRel);
        given(dissociationRel.dissociatingToken()).willReturn(secondToken);
        given(tokenRel.isAutomaticAssociation()).willReturn(true);
        given(tokenStore.loadPossiblyDeletedOrAutoRemovedToken(any())).willReturn(firstToken);
        given(tokenStore.loadTokenRelationship(any(), any()))
                .willReturn(secondRel)
                .willReturn(firstRel);

        // when:
        subject.dissociateUsing(List.of(dissociationRel), validator);

        // then:
        verify(dissociationRel).updateModelRelsSubjectTo(validator);
        assertEquals(numAssociations - 1, subject.getNumAssociations());
        assertEquals(numPositiveBalances, subject.getNumPositiveBalances());
        assertEquals(
                alreadyUsedAutoAssociations - 1, subject.getAlreadyUsedAutomaticAssociations());
    }

    @Test
    void dissociatingWorksWithNonZeroBalance() {
        // setup:
        final var alreadyAssocTokenId = new Id(0, 0, secondAssocTokenNum);
        final var dissociationRel = mock(Dissociation.class);
        final var tokenRel = mock(TokenRelationship.class);

        given(dissociationRel.dissociatingAccountId()).willReturn(subjectId);
        given(dissociationRel.dissociatedTokenId()).willReturn(alreadyAssocTokenId);
        given(dissociationRel.dissociatingAccountRel()).willReturn(tokenRel);
        given(dissociationRel.dissociatingToken()).willReturn(secondToken);
        given(tokenRel.isAutomaticAssociation()).willReturn(true);
        given(tokenRel.getBalance()).willReturn(100L);
        given(tokenStore.loadPossiblyDeletedOrAutoRemovedToken(any())).willReturn(firstToken);
        given(tokenStore.loadTokenRelationship(any(), any()))
                .willReturn(secondRel)
                .willReturn(firstRel);

        // when:
        subject.dissociateUsing(List.of(dissociationRel), validator);

        // then:
        verify(dissociationRel).updateModelRelsSubjectTo(validator);
        assertEquals(numAssociations - 1, subject.getNumAssociations());
        assertEquals(numPositiveBalances, subject.getNumPositiveBalances());
        assertEquals(
                alreadyUsedAutoAssociations - 1, subject.getAlreadyUsedAutomaticAssociations());
    }

    @Test
    void treasuryDissociationWorks() {
        // setup:
        final var alreadyAssocTokenId = new Id(0, 0, secondAssocTokenNum);
        final var dissociationRel = mock(Dissociation.class);
        final var tokenRel = mock(TokenRelationship.class);
        secondToken.setTreasury(subject);

        given(dissociationRel.dissociatingAccountId()).willReturn(subjectId);
        given(dissociationRel.dissociatedTokenId()).willReturn(alreadyAssocTokenId);
        given(dissociationRel.dissociatingAccountRel()).willReturn(tokenRel);
        given(dissociationRel.dissociatedTokenTreasuryRel()).willReturn(tokenRel);
        given(dissociationRel.dissociatingToken()).willReturn(secondToken);
        given(tokenRel.isAutomaticAssociation()).willReturn(true);
        given(tokenStore.loadPossiblyDeletedOrAutoRemovedToken(any())).willReturn(firstToken);
        given(tokenStore.loadTokenRelationship(any(), any()))
                .willReturn(secondRel)
                .willReturn(firstRel);

        // when:
        subject.dissociateUsing(List.of(dissociationRel), validator);

        // then:
        verify(dissociationRel).updateModelRelsSubjectTo(validator);
        assertEquals(numAssociations - 1, subject.getNumAssociations());
        assertEquals(numPositiveBalances, subject.getNumPositiveBalances());
        assertEquals(
                alreadyUsedAutoAssociations - 1, subject.getAlreadyUsedAutomaticAssociations());
    }

    @Test
    void dissociatingOnlyAssociationWorks() {
        // setup:
        final var alreadyAssocTokenId = new Id(0, 0, thirdAssocTokenNum);
        final var dissociationRel = mock(Dissociation.class);
        final var tokenRel = mock(TokenRelationship.class);

        given(dissociationRel.dissociatingAccountId()).willReturn(subjectId);
        given(dissociationRel.dissociatedTokenId()).willReturn(alreadyAssocTokenId);
        given(dissociationRel.dissociatingAccountRel()).willReturn(tokenRel);
        given(dissociationRel.dissociatingToken()).willReturn(thirdToken);
        given(tokenRel.isAutomaticAssociation()).willReturn(true);
        given(tokenStore.loadPossiblyDeletedOrAutoRemovedToken(any())).willReturn(thirdToken);
        subject.setNumAssociations(1);

        // when:
        subject.dissociateUsing(List.of(dissociationRel), validator);

        // then:
        verify(dissociationRel).updateModelRelsSubjectTo(validator);
        assertEquals(
                alreadyUsedAutoAssociations - 1, subject.getAlreadyUsedAutomaticAssociations());
    }

    @Test
    void dissociationFailsInvalidIfRelDoesntReferToUs() {
        // setup:
        final var notOurId = new Id(0, 0, 666);
        final var dissociationRel = mock(Dissociation.class);

        given(dissociationRel.dissociatingAccountId()).willReturn(notOurId);

        // expect:
        assertFailsWith(
                () -> subject.dissociateUsing(List.of(dissociationRel), validator), FAIL_INVALID);
    }

    @Test
    void failsOnAssociatingWithAlreadyRelatedToken() {
        // setup:
        final var alreadyAssocToken = new Token(new Id(0, 0, 666));
        given(tokenStore.hasAssociation(alreadyAssocToken, subject)).willReturn(true);

        // expect:
        assertFailsWith(
                () ->
                        subject.associateWith(
                                List.of(alreadyAssocToken),
                                tokenStore,
                                false,
                                false,
                                dynamicProperties),
                TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
    }

    @Test
    void failsOnCrossingAssociationLimit() {
        // setup:
        final var alreadyAssocToken = new Token(new Id(0, 0, 666));
        given(tokenStore.hasAssociation(alreadyAssocToken, subject)).willReturn(false);
        given(dynamicProperties.areTokenAssociationsLimited()).willReturn(true);
        given(dynamicProperties.maxTokensPerAccount()).willReturn(numAssociations);

        // expect:
        assertFailsWith(
                () ->
                        subject.associateWith(
                                List.of(alreadyAssocToken),
                                tokenStore,
                                false,
                                false,
                                dynamicProperties),
                TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);
    }

    @Test
    void canAssociateWithNewToken() {
        // setup:
        final var firstNewToken = new Token(new Id(0, 0, 888));
        final var secondNewToken = new Token(new Id(0, 0, 999));
        subject.setAutoAssociationMetadata(autoAssociationMetadata);
        given(dynamicProperties.areTokenAssociationsLimited()).willReturn(true);
        given(dynamicProperties.maxTokensPerAccount()).willReturn(numAssociations + 2);

        // when:
        subject.associateWith(
                List.of(firstNewToken, secondNewToken), tokenStore, true, true, dynamicProperties);

        // expect:
        assertEquals(numAssociations + 2, subject.getNumAssociations());
    }

    @Test
    void accountEqualsCheck() {
        // setup:
        var account = new Account(subjectId);
        account.setNumAssociations(numAssociations);
        account.setNumPositiveBalances(numPositiveBalances);
        account.setExpiry(1000L);
        account.initBalance(100L);
        account.setOwnedNfts(1L);
        account.incrementOwnedNfts();
        account.setMaxAutomaticAssociations(123);
        account.setAlreadyUsedAutomaticAssociations(12);
        account.setSmartContract(false);

        subject.setExpiry(1000L);
        subject.initBalance(100L);
        subject.setOwnedNfts(1L);
        subject.incrementOwnedNfts();
        subject.setAutoAssociationMetadata(account.getAutoAssociationMetadata());
        subject.setSmartContract(false);

        // when:
        var actualResult = subject.equals(account);

        // expect:
        assertEquals(account.getOwnedNfts(), subject.getOwnedNfts());
        // and:
        assertEquals(account.getId(), subject.getId());
        // and:
        assertEquals(account.getMaxAutomaticAssociations(), subject.getMaxAutomaticAssociations());
        assertEquals(
                account.getAlreadyUsedAutomaticAssociations(),
                subject.getAlreadyUsedAutomaticAssociations());
        assertEquals(subject.isSmartContract(), account.isSmartContract());
        assertTrue(actualResult);
    }

    @Test
    void accountHashCodeCheck() {
        // setup:
        subject.setOwnedNfts(0);
        var otherSubject = new Account(subjectId);
        otherSubject.incrementOwnedNfts();
        otherSubject.setNumAssociations(numAssociations);
        otherSubject.setNumPositiveBalances(numPositiveBalances);

        subject.incrementOwnedNfts();
        otherSubject.setAutoAssociationMetadata(autoAssociationMetadata);
        // when:
        var actualResult = subject.hashCode();

        // expect:
        assertEquals(otherSubject.hashCode(), actualResult);
    }

    @Test
    void cannotAutomaticallyAssociateIfLimitReaches() {
        final var firstNewToken = new Token(new Id(0, 0, 888));
        subject.setMaxAutomaticAssociations(maxAutoAssociations);
        subject.setAlreadyUsedAutomaticAssociations(maxAutoAssociations);

        assertFailsWith(
                () ->
                        subject.associateWith(
                                List.of(firstNewToken), tokenStore, true, true, dynamicProperties),
                NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
    }

    @Test
    void incrementsTheAlreadyUsedAutoAssociationAsExpected() {
        final var firstNewToken = new Token(new Id(0, 0, 888));
        subject.setMaxAutomaticAssociations(maxAutoAssociations);
        subject.setAlreadyUsedAutomaticAssociations(maxAutoAssociations - 1);

        subject.associateWith(List.of(firstNewToken), tokenStore, true, false, dynamicProperties);

        assertEquals(maxAutoAssociations, subject.getAlreadyUsedAutomaticAssociations());
    }

    @Test
    void invalidValuesToAlreadyUsedAutoAssociationsFailAsExpected() {
        assertFailsWith(
                () -> subject.setAlreadyUsedAutomaticAssociations(maxAutoAssociations + 1),
                NO_REMAINING_AUTOMATIC_ASSOCIATIONS);

        subject.setAlreadyUsedAutomaticAssociations(maxAutoAssociations);

        assertFailsWith(
                () -> subject.incrementUsedAutomaticAssociations(),
                NO_REMAINING_AUTOMATIC_ASSOCIATIONS);

        subject.setAlreadyUsedAutomaticAssociations(0);

        assertFailsWith(
                () -> subject.decrementUsedAutomaticAssociations(),
                NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
    }

    private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
        var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }
}
