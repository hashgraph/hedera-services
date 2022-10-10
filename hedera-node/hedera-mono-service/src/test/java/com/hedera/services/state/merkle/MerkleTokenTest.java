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
package com.hedera.services.state.merkle;

import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHts;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fractional;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.fees.CustomFeeBuilder;
import com.hederahashgraph.api.proto.java.CustomFee;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerkleTokenTest {
    private static final JKey adminKey = new JEd25519Key("not-a-real-admin-key".getBytes());
    private static final JKey otherAdminKey =
            new JEd25519Key("not-a-real-admin-key-either".getBytes());
    private static final JKey freezeKey = new JEd25519Key("not-a-real-freeze-key".getBytes());
    private static final JKey otherFreezeKey =
            new JEd25519Key("not-a-real-freeze-key-either".getBytes());
    private static final JKey wipeKey = new JEd25519Key("not-a-real-wipe-key".getBytes());
    private static final JKey otherWipeKey =
            new JEd25519Key("not-a-real-wipe-key-either".getBytes());
    private static final JKey supplyKey = new JEd25519Key("not-a-real-supply-key".getBytes());
    private static final JKey otherSupplyKey =
            new JEd25519Key("not-a-real-supply-key-either".getBytes());
    private static final JKey kycKey = new JEd25519Key("not-a-real-kyc-key".getBytes());
    private static final JKey otherKycKey = new JEd25519Key("not-a-real-kyc-key-either".getBytes());
    private static final JKey feeScheduleKey =
            new JEd25519Key("not-a-real-fee-schedule-key".getBytes());
    private static final JKey otherFeeScheduleKey =
            new JEd25519Key("not-a-real-fee-schedule-key-either".getBytes());
    private static final JKey pauseKey = new JEd25519Key("not-a-real-pause-key".getBytes());
    private static final JKey otherPauseKey =
            new JEd25519Key("not-a-real-pause-key-either".getBytes());

    private static final String symbol = "NotAnHbar";
    private static final String otherSymbol = "NotAnHbarEither";
    private static final String name = "NotAnHbarName";
    private static final String otherName = "NotAnHbarNameEither";
    private static final String memo = "NotAMemo";
    private static final String otherMemo = "NotAMemoEither";
    private static final int decimals = 2;
    private static final int otherDecimals = 3;
    private static final long expiry = 1_234_567L;
    private static final long otherExpiry = expiry + 2_345_678L;
    private static final long autoRenewPeriod = 1_234_567L;
    private static final long otherAutoRenewPeriod = 2_345_678L;
    private static final long totalSupply = 1_000_000L;
    private static final long otherTotalSupply = 1_000_001L;
    private static final boolean freezeDefault = true;
    private static final boolean otherFreezeDefault = false;
    private static final boolean accountsKycGrantedByDefault = true;
    private static final boolean otherAccountsKycGrantedByDefault = false;
    private static final EntityId treasury = new EntityId(1, 2, 3);
    private static final EntityId otherTreasury = new EntityId(3, 2, 1);
    private static final EntityId autoRenewAccount = new EntityId(2, 3, 4);
    private static final EntityId otherAutoRenewAccount = new EntityId(4, 3, 2);
    private static final boolean isDeleted = true;
    private static final boolean otherIsDeleted = false;
    private static final boolean isPaused = false;
    private static final boolean otherIsPaused = true;

    private static final long validNumerator = 5L;
    private static final long validDenominator = 100L;
    private static final long fixedUnitsToCollect = 7L;
    private static final long minimumUnitsToCollect = 1L;
    private static final long maximumUnitsToCollect = 55L;
    private static final EntityId denom = new EntityId(1, 2, 3);
    private static final EntityId feeCollector = new EntityId(4, 5, 6);
    private static final CustomFeeBuilder builder =
            new CustomFeeBuilder(feeCollector.toGrpcAccountId());
    private static final CustomFee fractionalFee =
            builder.withFractionalFee(
                    fractional(validNumerator, validDenominator)
                            .setMinimumAmount(minimumUnitsToCollect)
                            .setMaximumAmount(maximumUnitsToCollect));
    private static final CustomFee fixedFee =
            builder.withFixedFee(fixedHts(denom.toGrpcTokenId(), fixedUnitsToCollect));
    private static final List<CustomFee> grpcFeeSchedule = List.of(fixedFee, fractionalFee);
    private static final List<FcCustomFee> feeSchedule =
            grpcFeeSchedule.stream().map(FcCustomFee::fromGrpc).collect(toList());

    private final int number = 123_456;

    private MerkleToken subject;

    @BeforeEach
    void setup() {
        subject =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury,
                        number);
        setOptionalElements(subject);
        subject.setExpiry(expiry);
        subject.setTotalSupply(totalSupply);
        subject.setAdminKey(adminKey);
        subject.setFreezeKey(freezeKey);
        subject.setKycKey(kycKey);
        subject.setWipeKey(wipeKey);
        subject.setSupplyKey(supplyKey);
        subject.setDeleted(isDeleted);
        subject.setMemo(memo);
        subject.setTokenType(TokenType.FUNGIBLE_COMMON);
        subject.setSupplyType(TokenSupplyType.INFINITE);
        subject.setTreasury(treasury);
        subject.setName(name);
        subject.setSymbol(symbol);
        subject.setAccountsFrozenByDefault(true);
        subject.setFeeScheduleFrom(grpcFeeSchedule);
    }

    @Test
    void deleteIsNoop() {
        assertDoesNotThrow(subject::release);
    }

    @Test
    void copyWorks() {
        subject.setPauseKey(pauseKey);
        final var copySubject = subject.copy();

        assertNotSame(copySubject, subject);
        assertEquals(subject, copySubject);
        assertTrue(subject.isImmutable());
    }

    @Test
    void getterWorks() {
        assertEquals(feeSchedule, subject.customFeeSchedule());
    }

    @Test
    void objectContractHoldsForDifferentMemos() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);
        other.setMemo(otherMemo);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentTotalSupplies() {
        final var other =
                new MerkleToken(
                        expiry,
                        otherTotalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentDecimals() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        otherDecimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentWipeKey() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);
        other.setWipeKey(otherWipeKey);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentFeeScheduleKey() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);
        other.setFeeScheduleKey(otherFeeScheduleKey);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentPauseKey() {
        subject.setPauseKey(pauseKey);
        subject.setPaused(isPaused);
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);
        other.setPauseKey(otherPauseKey);
        other.setPaused(isPaused);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentSupply() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);
        other.setSupplyKey(otherSupplyKey);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentDeleted() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);
        other.setDeleted(otherIsDeleted);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentPaused() {
        subject.setPauseKey(pauseKey);
        subject.setPaused(isPaused);
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);
        other.setPauseKey(pauseKey);
        other.setPaused(otherIsPaused);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentAdminKey() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);
        other.setAdminKey(otherAdminKey);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentAutoRenewPeriods() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);
        other.setAutoRenewPeriod(otherAutoRenewPeriod);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentAutoRenewAccounts() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);
        other.setAutoRenewAccount(otherAutoRenewAccount);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentExpiries() {
        final var other =
                new MerkleToken(
                        otherExpiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentSymbol() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        otherSymbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentName() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        otherName,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentFreezeDefault() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        otherFreezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentAccountsKycGrantedByDefault() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        otherAccountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentTreasury() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        otherTreasury);
        setOptionalElements(other);

        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentKycKeys() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);

        other.setKycKey(otherKycKey);
        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());

        other.setKycKey(MerkleToken.UNUSED_KEY);
        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractHoldsForDifferentFreezeKeys() {
        final var other =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(other);

        other.setFreezeKey(otherFreezeKey);
        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());

        other.setFreezeKey(MerkleToken.UNUSED_KEY);
        assertNotEquals(subject, other);
        assertNotEquals(subject.hashCode(), other.hashCode());
    }

    @Test
    void objectContractPropertiesCheck() {
        subject.setPauseKey(pauseKey);
        assertTrue(subject.hasAdminKey());
        assertEquals(adminKey, subject.adminKey().get());
        assertEquals(freezeKey, subject.freezeKey().get());
        assertTrue(subject.hasFreezeKey());
        assertEquals(kycKey, subject.kycKey().get());
        assertTrue(subject.hasKycKey());
        assertEquals(supplyKey, subject.supplyKey().get());
        assertTrue(subject.hasSupplyKey());
        assertEquals(wipeKey, subject.wipeKey().get());
        assertTrue(subject.hasWipeKey());
        assertTrue(subject.isDeleted());
        assertEquals(symbol, subject.symbol());
        assertEquals(name, subject.name());
        assertTrue(subject.accountsKycGrantedByDefault());
        assertEquals(autoRenewAccount, subject.autoRenewAccount());
        assertTrue(subject.hasAutoRenewAccount());
        assertEquals(supplyKey, subject.getSupplyKey());
        assertEquals(wipeKey, subject.getWipeKey());
        assertEquals(pauseKey, subject.getPauseKey());
        assertEquals(adminKey, subject.getAdminKey());
        assertEquals(kycKey, subject.getKycKey());
        assertEquals(freezeKey, subject.getFreezeKey());
        assertEquals(memo, subject.memo());
        assertEquals(pauseKey, subject.pauseKey().get());
        assertTrue(subject.hasPauseKey());
    }

    @Test
    void hasFeeScheduleKeyWorks() {
        assertTrue(subject.hasFeeScheduleKey());

        subject.setFeeScheduleKey(feeScheduleKey);

        assertTrue(subject.hasFeeScheduleKey());
        assertEquals(feeScheduleKey, subject.getFeeScheduleKey());
        assertEquals(Optional.of(feeScheduleKey), subject.feeScheduleKey());
    }

    @Test
    void pausedWorks() {
        subject.setPaused(true);
        assertTrue(subject.isPaused());
    }

    private void setOptionalElements(final MerkleToken token) {
        token.setDeleted(isDeleted);
        token.setAdminKey(adminKey);
        token.setFreezeKey(freezeKey);
        token.setWipeKey(wipeKey);
        token.setSupplyKey(supplyKey);
        token.setKycKey(kycKey);
        token.setAutoRenewAccount(autoRenewAccount);
        token.setAutoRenewPeriod(autoRenewPeriod);
        token.setMemo(memo);
        token.setFeeScheduleFrom(grpcFeeSchedule);
        token.setFeeScheduleKey(feeScheduleKey);
        token.setTokenType(TokenType.FUNGIBLE_COMMON);
        token.setSupplyType(TokenSupplyType.INFINITE);
    }

    @Test
    void hashCodeContractMet() {
        final var defaultSubject = new MerkleAccountState();
        final var identicalSubject =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        setOptionalElements(identicalSubject);
        identicalSubject.setDeleted(isDeleted);
        identicalSubject.setTokenType(TokenType.FUNGIBLE_COMMON);
        identicalSubject.setSupplyType(TokenSupplyType.INFINITE);
        identicalSubject.setMaxSupply(subject.maxSupply());
        identicalSubject.setLastUsedSerialNumber(subject.getLastUsedSerialNumber());
        identicalSubject.setKey(EntityNum.fromInt(number));

        final var other =
                new MerkleToken(
                        otherExpiry,
                        otherTotalSupply,
                        otherDecimals,
                        otherSymbol,
                        otherName,
                        otherFreezeDefault,
                        otherAccountsKycGrantedByDefault,
                        otherTreasury);
        other.setTokenType(0);
        other.setSupplyType(0);
        other.setMaxSupply(subject.maxSupply());
        other.setLastUsedSerialNumber(subject.getLastUsedSerialNumber());

        assertNotEquals(subject.hashCode(), defaultSubject.hashCode());
        assertNotEquals(subject.hashCode(), other.hashCode());
        assertEquals(subject.hashCode(), identicalSubject.hashCode());
    }

    @Test
    void equalsWorksWithExtremes() {
        assertEquals(subject, subject);
        assertNotEquals(null, subject);
        assertNotEquals(new Object(), subject);
    }

    @Test
    void toStringWorks() {
        subject.setPauseKey(pauseKey);
        subject.setPaused(isPaused);
        final var desired =
                "MerkleToken{number=123456 <-> 0.0.123456, tokenType=FUNGIBLE_COMMON,"
                    + " supplyType=INFINITE, deleted=true, expiry=1234567, symbol=NotAnHbar,"
                    + " name=NotAnHbarName, memo=NotAMemo, treasury=1.2.3, maxSupply=0,"
                    + " totalSupply=1000000, decimals=2, lastUsedSerialNumber=0,"
                    + " autoRenewAccount=2.3.4, autoRenewPeriod=1234567, adminKey=ed25519:"
                    + " \"not-a-real-admin-key\"\n"
                    + ", kycKey=ed25519: \"not-a-real-kyc-key\"\n"
                    + ", wipeKey=ed25519: \"not-a-real-wipe-key\"\n"
                    + ", supplyKey=ed25519: \"not-a-real-supply-key\"\n"
                    + ", freezeKey=ed25519: \"not-a-real-freeze-key\"\n"
                    + ", pauseKey=ed25519: \"not-a-real-pause-key\"\n"
                    + ", accountsKycGrantedByDefault=true, accountsFrozenByDefault=true,"
                    + " pauseStatus=false, feeSchedules=[FcCustomFee{feeType=FIXED_FEE,"
                    + " fixedFee=FixedFeeSpec{unitsToCollect=7, tokenDenomination=1.2.3},"
                    + " feeCollector=EntityId{shard=4, realm=5, num=6},"
                    + " allCollectorsAreExempt=false}, FcCustomFee{feeType=FRACTIONAL_FEE,"
                    + " fractionalFee=FractionalFeeSpec{numerator=5, denominator=100,"
                    + " minimumUnitsToCollect=1, maximumUnitsToCollect=55, netOfTransfers=false},"
                    + " feeCollector=EntityId{shard=4, realm=5, num=6},"
                    + " allCollectorsAreExempt=false}], feeScheduleKey=<JEd25519Key: ed25519"
                    + " hex=6e6f742d612d7265616c2d6665652d7363686564756c652d6b6579>}";

        assertEquals(desired, subject.toString());
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(MerkleToken.CURRENT_VERSION, subject.getVersion());
        assertEquals(MerkleToken.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
        assertTrue(subject.isLeaf());
    }

    @Test
    void adjustsTotalSupplyWhenValid() {
        final var moreSupply = 500_000L;
        subject.adjustTotalSupplyBy(moreSupply);

        assertEquals(totalSupply + moreSupply, subject.totalSupply());
    }

    @Test
    void doesNotAdjustTotalSupplyWhenInvalid() {
        subject.setMaxSupply(2L);

        assertThrows(IllegalArgumentException.class, () -> subject.adjustTotalSupplyBy(1_500_000L));
    }

    @Test
    void throwsIaeIfTotalSupplyGoesNegative() {
        assertThrows(
                IllegalArgumentException.class, () -> subject.adjustTotalSupplyBy(-1_500_000L));
    }

    @Test
    void returnCorrectGrpcFeeSchedule() {
        final var token =
                new MerkleToken(
                        expiry,
                        totalSupply,
                        decimals,
                        symbol,
                        name,
                        freezeDefault,
                        accountsKycGrantedByDefault,
                        treasury);
        assertEquals(Collections.emptyList(), token.grpcFeeSchedule());

        token.setFeeScheduleFrom(grpcFeeSchedule);
        assertEquals(grpcFeeSchedule, token.grpcFeeSchedule());
    }

    @Test
    void canSetKey() {
        // setup:
        final var bigNum = (long) Integer.MAX_VALUE + 123;

        // given:
        subject.setKey(EntityNum.fromLong(bigNum));

        // then:
        assertEquals(BitPackUtils.codeFromNum(bigNum), subject.getKey().intValue());
    }
}
