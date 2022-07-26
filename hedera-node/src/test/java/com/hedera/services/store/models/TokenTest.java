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

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_METADATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERIAL_NUMBER_LIMIT_REACHED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TokenTest {
    private final JKey someKey = TxnHandlingScenario.TOKEN_SUPPLY_KT.asJKeyUnchecked();
    private final int numAssociations = 2;
    private final int numPositiveBalances = 1;
    private final long initialSupply = 1_000L;
    private final long initialTreasuryBalance = 500L;
    private final Id tokenId = new Id(1, 2, 3);
    private final Id treasuryId = new Id(0, 0, 0);
    private final Id nonTreasuryId = new Id(3, 2, 3);
    private final Account treasuryAccount = new Account(treasuryId);
    private final Account nonTreasuryAccount = new Account(nonTreasuryId);
    private final EntityNumPair treasuryAssociationKey =
            EntityNumPair.fromLongs(treasuryId.num(), tokenId.num());
    private final EntityNumPair nonTreasuryAssociationKey =
            EntityNumPair.fromLongs(nonTreasuryId.num(), tokenId.num());

    private Token subject;
    private TokenRelationship treasuryRel;
    private TokenRelationship nonTreasuryRel;

    @BeforeEach
    void setUp() {
        subject = new Token(tokenId);
        subject.initTotalSupply(initialSupply);
        subject.setTreasury(treasuryAccount);

        treasuryAccount.setNumPositiveBalances(numPositiveBalances);
        treasuryAccount.setNumAssociations(numAssociations);
        nonTreasuryAccount.setNumPositiveBalances(numPositiveBalances);
        nonTreasuryAccount.setNumAssociations(numAssociations);

        treasuryRel = new TokenRelationship(subject, treasuryAccount);
        treasuryRel.initBalance(initialTreasuryBalance);
        treasuryRel.setAutomaticAssociation(true);
        nonTreasuryRel = new TokenRelationship(subject, nonTreasuryAccount);
    }

    @Test
    void deleteAsExpected() {
        subject.setAdminKey(someKey);
        assertDoesNotThrow(() -> subject.delete());
    }

    @Test
    void recognizesFeeScheduleKey() {
        assertFalse(subject.hasFeeScheduleKey());

        subject.setFeeScheduleKey(TxnHandlingScenario.TOKEN_FEE_SCHEDULE_KT.asJKeyUnchecked());

        assertTrue(subject.hasFeeScheduleKey());
    }

    @Test
    void recognizesPauseKey() {
        assertFalse(subject.hasPauseKey());

        subject.setPauseKey(TxnHandlingScenario.TOKEN_PAUSE_KT.asJKeyUnchecked());

        assertTrue(subject.hasPauseKey());
    }

    @Test
    void changingPauseStatusFailsIfNoPauseKey() {
        assertFalse(subject.hasPauseKey());
        assertFailsWith(() -> subject.changePauseStatus(true), TOKEN_HAS_NO_PAUSE_KEY);
    }

    @Test
    void changingPauseStatusWorksIfTokenHasPauseKey() {
        subject.setPauseKey(TxnHandlingScenario.TOKEN_PAUSE_KT.asJKeyUnchecked());
        assertTrue(subject.hasPauseKey());

        subject.changePauseStatus(true);
        assertTrue(subject.isPaused());

        subject.changePauseStatus(false);
        assertFalse(subject.isPaused());
    }

    @Test
    void deleteFailsAsExpected() {
        subject.setAdminKey(null);
        assertFailsWith(() -> subject.delete(), TOKEN_IS_IMMUTABLE);
    }

    @Test
    void constructsOkToken() {
        final var feeScheduleKey = TxnHandlingScenario.TOKEN_FEE_SCHEDULE_KT.asKey();
        final var pauseKey = TxnHandlingScenario.TOKEN_PAUSE_KT.asKey();
        final var op =
                TransactionBody.newBuilder()
                        .setTokenCreation(
                                TokenCreateTransactionBody.newBuilder()
                                        .setTokenType(
                                                com.hederahashgraph.api.proto.java.TokenType
                                                        .FUNGIBLE_COMMON)
                                        .setInitialSupply(25)
                                        .setMaxSupply(21_000_000)
                                        .setSupplyType(
                                                com.hederahashgraph.api.proto.java.TokenSupplyType
                                                        .FINITE)
                                        .setDecimals(10)
                                        .setFreezeDefault(false)
                                        .setMemo("the mother")
                                        .setName("bitcoin")
                                        .setSymbol("BTC")
                                        .setFeeScheduleKey(feeScheduleKey)
                                        .setPauseKey(pauseKey)
                                        .addAllCustomFees(
                                                List.of(
                                                        CustomFee.newBuilder()
                                                                .setFixedFee(
                                                                        FixedFee.newBuilder()
                                                                                .setAmount(10)
                                                                                .build())
                                                                .setFeeCollectorAccountId(
                                                                        IdUtils.asAccount("1.2.3"))
                                                                .build()))
                                        .setAutoRenewAccount(
                                                nonTreasuryAccount.getId().asGrpcAccount())
                                        .setExpiry(Timestamp.newBuilder().setSeconds(1000L).build())
                                        .build())
                        .build();

        subject =
                Token.fromGrpcOpAndMeta(
                        tokenId, op.getTokenCreation(), treasuryAccount, nonTreasuryAccount, 123);

        assertEquals("bitcoin", subject.getName());
        assertEquals(123L, subject.getExpiry());
        assertEquals(TokenSupplyType.FINITE, subject.getSupplyType());
        assertNotNull(subject.getFeeScheduleKey());
        assertNotNull(subject.getPauseKey());
        assertFalse(subject.isPaused());
    }

    @Test
    void okCreationRelationship() {
        final var frzKey = TxnHandlingScenario.TOKEN_FREEZE_KT.asJKeyUnchecked();
        final var kycKey = TxnHandlingScenario.TOKEN_KYC_KT.asJKeyUnchecked();
        subject.setFreezeKey(frzKey);
        subject.setKycKey(kycKey);
        final var rel = subject.newEnabledRelationship(treasuryAccount);
        assertNotNull(rel);
        assertFalse(rel.isFrozen());
        assertTrue(rel.isKycGranted());
    }

    @Test
    void constructsTreasuryRelationShipAsExpected() {
        subject.setKycKey(someKey);
        final var newRel = subject.newRelationshipWith(treasuryAccount, true);
        newRel.initBalance(initialTreasuryBalance);

        assertEquals(newRel, treasuryRel);
    }

    @Test
    void constructsExpectedDefaultRelWithNoKeys() {
        // setup:
        nonTreasuryRel.setKycGranted(true);

        // when:
        final var newRel = subject.newRelationshipWith(nonTreasuryAccount, false);

        // then:
        assertEquals(newRel, nonTreasuryRel);
    }

    @Test
    void markAutoRemovedWorks() {
        // expect:
        assertFalse(subject.isBelievedToHaveBeenAutoRemoved());

        // when:
        subject.markAutoRemoved();

        // then:
        assertTrue(subject.isBelievedToHaveBeenAutoRemoved());
    }

    @Test
    void constructsExpectedDefaultRelWithFreezeKeyAndFrozenByDefault() {
        // setup:
        nonTreasuryRel.setFrozen(true);
        nonTreasuryRel.setKycGranted(true);

        // given:
        subject.setFreezeKey(someKey);
        subject.setFrozenByDefault(true);

        // when:
        final var newRel = subject.newRelationshipWith(nonTreasuryAccount, false);

        // then:
        assertEquals(newRel, nonTreasuryRel);
    }

    @Test
    void constructsExpectedDefaultRelWithFreezeKeyAndNotFrozenByDefault() {
        // setup:
        nonTreasuryRel.setKycGranted(true);

        // given:
        subject.setFreezeKey(someKey);
        subject.setFrozenByDefault(false);

        // when:
        final var newRel = subject.newRelationshipWith(nonTreasuryAccount, false);

        // then:
        assertEquals(newRel, nonTreasuryRel);
    }

    @Test
    void constructsExpectedDefaultRelWithKycKeyOnly() {
        // given:
        subject.setKycKey(someKey);

        // when:
        final var newRel = subject.newRelationshipWith(nonTreasuryAccount, false);

        // then:
        assertEquals(newRel, nonTreasuryRel);
    }

    @Test
    void failsInvalidIfLogicImplTriesToChangeNonTreasurySupply() {
        assertFailsWith(() -> subject.burn(nonTreasuryRel, 1L), FAIL_INVALID);
        assertFailsWith(() -> subject.mint(nonTreasuryRel, 1L, false), FAIL_INVALID);
    }

    @Test
    void cantBurnOrMintNegativeAmounts() {
        assertFailsWith(() -> subject.burn(treasuryRel, -1L), INVALID_TOKEN_BURN_AMOUNT);
        assertFailsWith(() -> subject.mint(treasuryRel, -1L, false), INVALID_TOKEN_MINT_AMOUNT);
    }

    @Test
    void cantBurnOrMintWithoutSupplyKey() {
        subject.setSupplyKey(null);
        subject.setType(TokenType.FUNGIBLE_COMMON);
        assertFailsWith(() -> subject.burn(treasuryRel, 1L), TOKEN_HAS_NO_SUPPLY_KEY);
        assertFailsWith(() -> subject.mint(treasuryRel, 1L, false), TOKEN_HAS_NO_SUPPLY_KEY);
    }

    @Test
    void cannotChangeTreasuryBalanceToNegative() {
        // given:
        subject.setSupplyKey(someKey);
        subject.setType(TokenType.FUNGIBLE_COMMON);
        subject.initSupplyConstraints(TokenSupplyType.FINITE, 10000);

        assertFailsWith(
                () -> subject.burn(treasuryRel, initialTreasuryBalance + 1),
                INSUFFICIENT_TOKEN_BALANCE);
    }

    @Test
    void cannotChangeSupplyToNegative() {
        // setup:
        final long overflowMint = Long.MAX_VALUE - initialSupply + 1L;

        // given:
        subject.setSupplyKey(someKey);
        subject.setType(TokenType.FUNGIBLE_COMMON);

        assertFailsWith(
                () -> subject.mint(treasuryRel, overflowMint, false), INVALID_TOKEN_MINT_AMOUNT);
        assertFailsWith(
                () -> subject.burn(treasuryRel, initialSupply + 1), INVALID_TOKEN_BURN_AMOUNT);
    }

    @Test
    void burnsAsExpected() {
        subject.setType(TokenType.FUNGIBLE_COMMON);
        subject.initSupplyConstraints(TokenSupplyType.FINITE, 20000L);
        final long burnAmount = 100L;

        // given:
        subject.setSupplyKey(someKey);

        // when:
        subject.burn(treasuryRel, burnAmount);

        // then:
        assertEquals(initialSupply - burnAmount, subject.getTotalSupply());
        assertEquals(-burnAmount, treasuryRel.getBalanceChange());
        assertEquals(initialTreasuryBalance - burnAmount, treasuryRel.getBalance());
    }

    @Test
    void burnsUniqueAsExpected() {
        treasuryRel.initBalance(2);
        subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        subject.initSupplyConstraints(TokenSupplyType.FINITE, 20000L);
        subject.setSupplyKey(someKey);
        subject.setLoadedUniqueTokens(
                Map.of(
                        10L, new UniqueToken(subject.getId(), 10L, treasuryId),
                        11L, new UniqueToken(subject.getId(), 11L, treasuryId)));
        final var ownershipTracker = mock(OwnershipTracker.class);
        final long serialNumber0 = 10L;
        final long serialNumber1 = 11L;

        subject.burn(ownershipTracker, treasuryRel, List.of(serialNumber0, serialNumber1));

        assertEquals(initialSupply - 2, subject.getTotalSupply());
        assertEquals(-2, treasuryRel.getBalanceChange());
        verify(ownershipTracker)
                .add(subject.getId(), OwnershipTracker.forRemoving(treasuryId, serialNumber0));
        verify(ownershipTracker)
                .add(subject.getId(), OwnershipTracker.forRemoving(treasuryId, serialNumber1));
        assertTrue(subject.hasRemovedUniqueTokens());
        final var removedUniqueTokens = subject.removedUniqueTokens();
        assertEquals(2, removedUniqueTokens.size());
        assertEquals(serialNumber0, removedUniqueTokens.get(0).getSerialNumber());
        assertEquals(serialNumber1, removedUniqueTokens.get(1).getSerialNumber());
        assertEquals(numPositiveBalances - 1, treasuryAccount.getNumPositiveBalances());
    }

    @Test
    void mintsUniqueAsExpected() {
        treasuryRel.initBalance(0);
        subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        subject.initSupplyConstraints(TokenSupplyType.FINITE, 20000L);
        subject.setSupplyKey(someKey);

        final var ownershipTracker = mock(OwnershipTracker.class);
        subject.mint(
                ownershipTracker,
                treasuryRel,
                List.of(ByteString.copyFromUtf8("memo")),
                RichInstant.fromJava(Instant.now()));
        assertEquals(initialSupply + 1, subject.getTotalSupply());
        assertEquals(1, treasuryRel.getBalanceChange());
        verify(ownershipTracker).add(eq(subject.getId()), Mockito.any());
        assertTrue(subject.hasMintedUniqueTokens());
        assertEquals(1, subject.mintedUniqueTokens().get(0).getSerialNumber());
        assertEquals(1, subject.getLastUsedSerialNumber());
        assertEquals(TokenType.NON_FUNGIBLE_UNIQUE, subject.getType());
        assertEquals(numPositiveBalances + 1, treasuryAccount.getNumPositiveBalances());
    }

    @Test
    void mintsAsExpected() {
        final long mintAmount = 100L;
        subject.setType(TokenType.FUNGIBLE_COMMON);
        subject.initSupplyConstraints(TokenSupplyType.FINITE, 100000);
        // given:
        subject.setSupplyKey(someKey);

        // when:
        subject.mint(treasuryRel, mintAmount, false);

        // then:
        assertEquals(initialSupply + mintAmount, subject.getTotalSupply());
        assertEquals(+mintAmount, treasuryRel.getBalanceChange());
        assertEquals(initialTreasuryBalance + mintAmount, treasuryRel.getBalance());
    }

    @Test
    void wipesCommonAsExpected() {
        subject.setType(TokenType.FUNGIBLE_COMMON);
        subject.initSupplyConstraints(TokenSupplyType.FINITE, 100000);
        subject.setSupplyKey(someKey);
        subject.setWipeKey(someKey);
        nonTreasuryRel.setBalance(100);

        subject.wipe(nonTreasuryRel, 10);
        assertEquals(initialSupply - 10, subject.getTotalSupply());
        assertEquals(90, nonTreasuryRel.getBalance());
        assertEquals(numPositiveBalances, nonTreasuryAccount.getNumPositiveBalances());

        nonTreasuryRel.setBalance(30);

        subject.wipe(nonTreasuryRel, 30);
        assertEquals(initialSupply - 40, subject.getTotalSupply());
        assertEquals(0, nonTreasuryRel.getBalance());
        assertEquals(numPositiveBalances - 1, nonTreasuryAccount.getNumPositiveBalances());
    }

    @Test
    void failsWipingCommonAsExpected() {
        // common setup
        subject.setType(TokenType.FUNGIBLE_COMMON);
        subject.initSupplyConstraints(TokenSupplyType.FINITE, 100000);
        subject.setSupplyKey(someKey);
        // no wipe key
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(nonTreasuryRel, 10));

        subject.setWipeKey(someKey);
        // negative amount
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(nonTreasuryRel, -10));

        // wipe treasury
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(treasuryRel, 10));

        // negate total supply
        subject.setTotalSupply(10);
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(nonTreasuryRel, 11));

        // negate account balance
        nonTreasuryRel.setBalance(0);
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(nonTreasuryRel, 5));

        subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(nonTreasuryRel, 5));
    }

    @Test
    void wipesUniqueAsExpected() {
        subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        subject.initSupplyConstraints(TokenSupplyType.FINITE, 100000);
        subject.setSupplyKey(someKey);
        subject.setWipeKey(someKey);

        final var loadedUniqueTokensMap = (HashMap<Long, UniqueToken>) mock(HashMap.class);
        final var uniqueToken = mock(UniqueToken.class);
        final var owner = nonTreasuryAccount.getId();
        given(uniqueToken.getOwner()).willReturn(owner);
        given(loadedUniqueTokensMap.get(any())).willReturn(uniqueToken);
        subject.setLoadedUniqueTokens(loadedUniqueTokensMap);

        nonTreasuryRel.setBalance(100);

        final var ownershipTracker = mock(OwnershipTracker.class);
        subject.wipe(ownershipTracker, nonTreasuryRel, List.of(1L));
        assertEquals(initialSupply - 1, subject.getTotalSupply());
        assertEquals(99, nonTreasuryRel.getBalanceChange());
        assertEquals(99, nonTreasuryRel.getBalance());
        verify(ownershipTracker).add(eq(subject.getId()), Mockito.any());
        assertTrue(subject.hasRemovedUniqueTokens());
        assertEquals(1, subject.removedUniqueTokens().get(0).getSerialNumber());
        assertTrue(subject.hasChangedSupply());
        assertEquals(100000, subject.getMaxSupply());
        assertEquals(numPositiveBalances, nonTreasuryAccount.getNumPositiveBalances());

        nonTreasuryRel.setBalance(2);

        subject.wipe(ownershipTracker, nonTreasuryRel, List.of(1L, 2L));

        assertEquals(initialSupply - 3, subject.getTotalSupply());
        assertEquals(0, nonTreasuryRel.getBalanceChange());
        assertEquals(0, nonTreasuryRel.getBalance());
        assertTrue(subject.hasRemovedUniqueTokens());
        assertTrue(subject.hasChangedSupply());
        assertEquals(100000, subject.getMaxSupply());
        assertEquals(numPositiveBalances - 1, nonTreasuryAccount.getNumPositiveBalances());
    }

    @Test
    void uniqueWipeFailsAsExpected() {
        subject.setType(TokenType.FUNGIBLE_COMMON);
        subject.initSupplyConstraints(TokenSupplyType.FINITE, 100000);
        subject.setSupplyKey(someKey);

        final Map<Long, UniqueToken> loadedUniqueTokensMap = new HashMap<>();
        subject.setLoadedUniqueTokens(loadedUniqueTokensMap);

        final var ownershipTracker = mock(OwnershipTracker.class);
        final var singleSerialNumber = List.of(1L);

        /* Invalid to wipe serial numbers for a FUNGIBLE_COMMON token */
        assertFailsWith(
                () -> subject.wipe(ownershipTracker, nonTreasuryRel, singleSerialNumber),
                FAIL_INVALID);

        subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);

        /* Must have a wipe key */
        assertFailsWith(
                () -> subject.wipe(ownershipTracker, treasuryRel, singleSerialNumber),
                TOKEN_HAS_NO_WIPE_KEY);

        /* Not allowed to wipe treasury */
        subject.setWipeKey(someKey);
        assertFailsWith(
                () -> subject.wipe(ownershipTracker, treasuryRel, singleSerialNumber),
                CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT);
    }

    @Test
    void uniqueBurnFailsAsExpected() {
        subject.setType(TokenType.FUNGIBLE_COMMON);
        subject.initSupplyConstraints(TokenSupplyType.FINITE, 100000);
        subject.setSupplyKey(someKey);
        final var ownershipTracker = mock(OwnershipTracker.class);
        final List<Long> emptySerialNumber = List.of();
        final var singleSerialNumber = List.of(1L);

        assertThrows(
                InvalidTransactionException.class,
                () -> {
                    subject.burn(ownershipTracker, treasuryRel, singleSerialNumber);
                });

        subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        assertFailsWith(
                () -> subject.burn(ownershipTracker, treasuryRel, emptySerialNumber),
                INVALID_TOKEN_BURN_METADATA);
    }

    @Test
    void canOnlyBurnTokensOwnedByTreasury() {
        // setup:
        final var ownershipTracker = mock(OwnershipTracker.class);
        final var oneToBurn = new UniqueToken(subject.getId(), 1L, nonTreasuryId);

        // given:
        subject.setSupplyKey(someKey);
        subject.setLoadedUniqueTokens(Map.of(1L, oneToBurn));
        subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);

        // expect:
        assertFailsWith(
                () -> subject.burn(ownershipTracker, treasuryRel, List.of(1L)),
                TREASURY_MUST_OWN_BURNED_NFT);

        // and when:
        oneToBurn.setOwner(treasuryId);
        assertDoesNotThrow(() -> subject.burn(ownershipTracker, treasuryRel, List.of(1L)));
    }

    @Test
    void cannotMintPastSerialNoLimit() {
        // setup:
        final var twoMeta = List.of(ByteString.copyFromUtf8("A"), ByteString.copyFromUtf8("Z"));
        subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        subject.setLastUsedSerialNumber(BitPackUtils.MAX_NUM_ALLOWED - 1);

        assertFailsWith(
                () -> subject.mint(null, treasuryRel, twoMeta, RichInstant.MISSING_INSTANT),
                SERIAL_NUMBER_LIMIT_REACHED);
    }

    @Test
    void uniqueMintFailsAsExpected() {
        subject.setType(TokenType.FUNGIBLE_COMMON);
        subject.initSupplyConstraints(TokenSupplyType.FINITE, 100000);
        subject.setSupplyKey(someKey);
        final var ownershipTracker = mock(OwnershipTracker.class);
        final var metadata = List.of(ByteString.copyFromUtf8("memo"));
        final List<ByteString> emptyMetadata = List.of();

        assertThrows(
                InvalidTransactionException.class,
                () -> {
                    subject.mint(
                            ownershipTracker,
                            treasuryRel,
                            metadata,
                            RichInstant.fromJava(Instant.now()));
                });

        subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        assertThrows(
                InvalidTransactionException.class,
                () -> {
                    subject.mint(
                            ownershipTracker,
                            treasuryRel,
                            emptyMetadata,
                            RichInstant.fromJava(Instant.now()));
                });
    }

    @Test
    void objectContractWorks() {
        subject.setLastUsedSerialNumber(1);
        assertEquals(1, subject.getLastUsedSerialNumber());
        subject.setFrozenByDefault(false);
        assertFalse(subject.isFrozenByDefault());

        final var wipeKey = TxnHandlingScenario.TOKEN_WIPE_KT.asJKeyUnchecked();
        subject.setWipeKey(wipeKey);
        assertEquals(wipeKey, subject.getWipeKey());

        final var account = new Account(Id.DEFAULT);
        subject.setTreasury(account);
        assertEquals(account, subject.getTreasury());

        subject.setAutoRenewAccount(account);
        assertEquals(account, subject.getAutoRenewAccount());

        final var hmap = new HashMap<Long, UniqueToken>();
        hmap.put(1L, new UniqueToken(new Id(1, 2, 3), 4));
        subject.setLoadedUniqueTokens(hmap);
        assertEquals(hmap, subject.getLoadedUniqueTokens());

        subject.setType(TokenType.FUNGIBLE_COMMON);
        assertTrue(subject.isFungibleCommon());
        assertFalse(subject.isNonFungibleUnique());

        subject.setNew(true);
        assertTrue(subject.isNew());
    }

    @Test
    void reflectionObjectHelpersWork() {
        final var otherToken = new Token(new Id(1, 2, 3));

        assertNotEquals(subject, otherToken);
        assertNotEquals(subject.hashCode(), otherToken.hashCode());
    }

    @Test
    void toStringWorks() {
        final var desired =
                "Token{id=1.2.3, type=null, deleted=false, autoRemoved=false,"
                    + " treasury=Account{id=0.0.0, expiry=0, balance=0, deleted=false, ownedNfts=0,"
                    + " alreadyUsedAutoAssociations=0, maxAutoAssociations=0, alias=,"
                    + " cryptoAllowances=null, fungibleTokenAllowances=null,"
                    + " approveForAllNfts=null, numAssociations=2, numPositiveBalances=1,"
                    + " ethereumNonce=0}, autoRenewAccount=null, kycKey=<N/A>, freezeKey=<N/A>,"
                    + " frozenByDefault=false, supplyKey=<N/A>, currentSerialNumber=0,"
                    + " pauseKey=<N/A>, paused=false}";

        assertEquals(desired, subject.toString());
    }
}
