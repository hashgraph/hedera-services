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
package com.hedera.services.fees.calculation;

import static com.hedera.services.fees.calculation.BasicFcfsUsagePrices.DEFAULT_RESOURCE_PRICES;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UNRECOGNIZED;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.SCHEDULE_CREATE_CONTRACT_CALL;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.google.common.io.Files;
import com.hedera.services.files.HederaFs;
import com.hedera.services.files.interceptors.MockFileNumbers;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.mocks.MockAppender;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.File;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BasicFcfsUsagePricesTest {
    public static final String R4_FEE_SCHEDULE_REPR_PATH =
            "src/test/resources/testfiles/r4FeeSchedule.bin";

    FileID schedules = IdUtils.asFile("0.0.111");
    long currentExpiry = 1_234_567;
    long nextExpiry = currentExpiry + 1_000;
    FeeComponents currResourceUsagePrices =
            FeeComponents.newBuilder()
                    .setMin(currentExpiry)
                    .setMax(currentExpiry)
                    .setBpr(1_000_000L)
                    .setBpt(2_000_000L)
                    .setRbh(3_000_000L)
                    .setSbh(4_000_000L)
                    .build();
    FeeComponents nextResourceUsagePrices =
            FeeComponents.newBuilder()
                    .setMin(nextExpiry)
                    .setMax(nextExpiry)
                    .setBpr(2_000_000L)
                    .setBpt(3_000_000L)
                    .setRbh(4_000_000L)
                    .setSbh(5_000_000L)
                    .build();
    FeeData currUsagePrices =
            FeeData.newBuilder()
                    .setNetworkdata(currResourceUsagePrices)
                    .setNodedata(currResourceUsagePrices)
                    .setServicedata(currResourceUsagePrices)
                    .build();
    FeeData nextUsagePrices =
            FeeData.newBuilder()
                    .setNetworkdata(nextResourceUsagePrices)
                    .setNodedata(nextResourceUsagePrices)
                    .setServicedata(nextResourceUsagePrices)
                    .build();

    Map<SubType, FeeData> currUsagePricesMap = Map.of(DEFAULT, currUsagePrices);
    Map<SubType, FeeData> nextUsagePricesMap = Map.of(DEFAULT, nextUsagePrices);

    Map<SubType, FeeData> nextContractCallPrices = currUsagePricesMap;
    Map<SubType, FeeData> currentContractCallPrices = nextUsagePricesMap;

    FeeSchedule nextFeeSchedule, currentFeeSchedule;
    CurrentAndNextFeeSchedule feeSchedules;

    BasicFcfsUsagePrices subject;

    TransactionBody contractCallTxn =
            TransactionBody.newBuilder()
                    .setTransactionID(
                            TransactionID.newBuilder()
                                    .setTransactionValidStart(
                                            Timestamp.newBuilder().setSeconds(nextExpiry + 1)))
                    .setContractCall(
                            ContractCallTransactionBody.newBuilder()
                                    .setContractID(IdUtils.asContract("1.2.3")))
                    .build();

    HederaFs hfs;
    PlatformTxnAccessor accessor;

    @BeforeEach
    void setup() {
        nextFeeSchedule =
                FeeSchedule.newBuilder()
                        .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(nextExpiry))
                        .addTransactionFeeSchedule(
                                TransactionFeeSchedule.newBuilder()
                                        .setHederaFunctionality(ContractCall)
                                        .addFees(nextContractCallPrices.get(DEFAULT)))
                        .build();
        currentFeeSchedule =
                FeeSchedule.newBuilder()
                        .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(currentExpiry))
                        .addTransactionFeeSchedule(
                                TransactionFeeSchedule.newBuilder()
                                        .setHederaFunctionality(ContractCall)
                                        .addFees(currentContractCallPrices.get(DEFAULT)))
                        .build();
        feeSchedules =
                CurrentAndNextFeeSchedule.newBuilder()
                        .setCurrentFeeSchedule(currentFeeSchedule)
                        .setNextFeeSchedule(nextFeeSchedule)
                        .build();

        hfs = mock(HederaFs.class);
        given(hfs.exists(schedules)).willReturn(true);
        given(hfs.cat(schedules)).willReturn(feeSchedules.toByteArray());

        accessor = mock(PlatformTxnAccessor.class);
        given(accessor.getTxn()).willReturn(contractCallTxn);
        given(accessor.getTxnId()).willReturn(contractCallTxn.getTransactionID());
        given(accessor.getFunction()).willReturn(ContractCall);

        subject = new BasicFcfsUsagePrices(hfs, new MockFileNumbers());
    }

    @Test
    void returnsExpectedPriceSequence() {
        // 00+00
        subject.loadPriceSchedules();

        // when:
        var actual = subject.activePricingSequence(ContractCall);

        // then:
        assertEquals(
                Triple.of(
                        currentContractCallPrices,
                        Instant.ofEpochSecond(currentExpiry),
                        nextContractCallPrices),
                actual);
    }

    @Test
    void getsActivePrices() {
        // given:
        subject.loadPriceSchedules();

        // when:
        Map<SubType, FeeData> actual = subject.activePrices(accessor);

        // then:
        assertEquals(nextUsagePricesMap, actual);
    }

    @Test
    void getsDefaultPricesIfActiveTxnInvalid() {
        // given:
        subject.loadPriceSchedules();
        // and:
        given(accessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
        given(accessor.getFunction()).willReturn(UNRECOGNIZED);

        // when:
        Map<SubType, FeeData> actual = subject.activePrices(accessor);

        // then:
        assertEquals(DEFAULT_RESOURCE_PRICES, actual);
    }

    @Test
    void getsTransferUsagePricesAtCurrent() {
        // given:
        subject.loadPriceSchedules();
        Timestamp at = Timestamp.newBuilder().setSeconds(currentExpiry - 1).build();

        // when:
        Map<SubType, FeeData> actual = subject.pricesGiven(ContractCall, at);

        // then:
        assertEquals(currentContractCallPrices, actual);
    }

    @Test
    void returnsDefaultUsagePricesForUnsupported() {
        // setup:
        MockAppender mockAppender = new MockAppender();
        var log =
                (org.apache.logging.log4j.core.Logger)
                        LogManager.getLogger(BasicFcfsUsagePrices.class);
        log.addAppender(mockAppender);
        Level levelForReset = log.getLevel();
        log.setLevel(Level.DEBUG);

        // given:
        subject.loadPriceSchedules();
        Timestamp at = Timestamp.newBuilder().setSeconds(currentExpiry - 1).build();

        // when:
        Map<SubType, FeeData> actual = subject.pricesGiven(UNRECOGNIZED, at);

        // then:
        assertEquals(DEFAULT_RESOURCE_PRICES, actual);
        assertEquals(1, mockAppender.size());
        assertEquals(
                "DEBUG - Default usage price will be used, no specific usage prices available for"
                        + " function UNRECOGNIZED @ 1970-01-15T06:56:06Z!",
                mockAppender.get(0));

        // tearDown:
        log.setLevel(levelForReset);
        log.removeAppender(mockAppender);
        mockAppender.clear();
    }

    @Test
    void getsTransferUsagePricesPastCurrentBeforeNextExpiry() throws Exception {
        // given:
        subject.loadPriceSchedules();
        Timestamp at = Timestamp.newBuilder().setSeconds(nextExpiry - 1).build();

        // when:
        Map<SubType, FeeData> actual = subject.pricesGiven(ContractCall, at);

        // then:
        assertEquals(nextContractCallPrices, actual);
    }

    @Test
    void loadsGoodScheduleUneventfully() throws Exception {
        // setup:
        byte[] bytes = Files.toByteArray(new File(R4_FEE_SCHEDULE_REPR_PATH));
        CurrentAndNextFeeSchedule expectedFeeSchedules = CurrentAndNextFeeSchedule.parseFrom(bytes);

        given(hfs.exists(schedules)).willReturn(true);
        given(hfs.cat(schedules)).willReturn(bytes);

        // when:
        subject.loadPriceSchedules();

        // then:
        assertEquals(expectedFeeSchedules, subject.feeSchedules);
    }

    @Test
    void throwsNfseOnMissingScheduleInFcfs() {
        given(hfs.exists(schedules)).willReturn(false);

        // expect:
        assertThrows(IllegalStateException.class, () -> subject.loadPriceSchedules());
    }

    @Test
    void throwsNfseOnBadScheduleInFcfs() {
        given(hfs.exists(schedules)).willReturn(true);
        given(hfs.cat(any())).willReturn("NONSENSE".getBytes());

        // expect:
        assertThrows(IllegalStateException.class, () -> subject.loadPriceSchedules());
    }

    @Test
    void usesDefaultPricesForUnexpectedFailure() {
        given(accessor.getFunction()).willThrow(IllegalStateException.class);

        // when:
        var prices = subject.activePrices(accessor);

        // then:
        assertEquals(DEFAULT_RESOURCE_PRICES, prices);
    }

    @Test
    void translatesFromUntypedFeeSchedule() {
        // setup:
        final var fcPrices = currUsagePrices.toBuilder().setSubType(TOKEN_FUNGIBLE_COMMON).build();
        final var fcFeesPrices =
                currUsagePrices.toBuilder()
                        .setSubType(TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES)
                        .build();
        final var unPrices =
                currUsagePrices.toBuilder().setSubType(TOKEN_NON_FUNGIBLE_UNIQUE).build();
        final var unFeesPrices =
                currUsagePrices.toBuilder()
                        .setSubType(TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES)
                        .build();
        // given:
        final var inferred = subject.functionUsagePricesFrom(getPreSubTypeFeeSchedule());
        // and:
        final var xferTypedPricesMap = inferred.get(CryptoTransfer);
        final var mintTypedPricesMap = inferred.get(TokenMint);
        final var burnTypedPricesMap = inferred.get(TokenBurn);
        final var wipeTypedPricesMap = inferred.get(TokenAccountWipe);

        // expect:
        assertEquals(5, xferTypedPricesMap.size());
        assertEquals(2, mintTypedPricesMap.size());
        assertEquals(2, burnTypedPricesMap.size());
        assertEquals(2, wipeTypedPricesMap.size());
        // and:
        assertEquals(currUsagePrices, xferTypedPricesMap.get(DEFAULT));
        assertEquals(fcPrices, xferTypedPricesMap.get(SubType.TOKEN_FUNGIBLE_COMMON));
        assertEquals(
                fcFeesPrices,
                xferTypedPricesMap.get(SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES));
        assertEquals(unPrices, xferTypedPricesMap.get(SubType.TOKEN_NON_FUNGIBLE_UNIQUE));
        assertEquals(
                unFeesPrices,
                xferTypedPricesMap.get(SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES));
        assertEquals(fcPrices, mintTypedPricesMap.get(SubType.TOKEN_FUNGIBLE_COMMON));
        assertEquals(unPrices, mintTypedPricesMap.get(SubType.TOKEN_NON_FUNGIBLE_UNIQUE));
        assertEquals(fcPrices, burnTypedPricesMap.get(SubType.TOKEN_FUNGIBLE_COMMON));
        assertEquals(unPrices, burnTypedPricesMap.get(SubType.TOKEN_NON_FUNGIBLE_UNIQUE));
        assertEquals(fcPrices, wipeTypedPricesMap.get(SubType.TOKEN_FUNGIBLE_COMMON));
        assertEquals(unPrices, wipeTypedPricesMap.get(SubType.TOKEN_NON_FUNGIBLE_UNIQUE));
    }

    @Test
    void usesNewStylePricesIfAvailable() {
        // setup:
        final var data =
                tfsWithOldAndNewPriceData(
                        currUsagePrices,
                        nextUsagePrices,
                        DEFAULT,
                        TOKEN_FUNGIBLE_COMMON,
                        TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES,
                        TOKEN_NON_FUNGIBLE_UNIQUE,
                        TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES,
                        SCHEDULE_CREATE_CONTRACT_CALL);
        final Map<SubType, FeeData> pricesMap = new EnumMap<>(SubType.class);

        // when:
        subject.ensurePricesMapHasRequiredTypes(
                data,
                pricesMap,
                EnumSet.of(
                        DEFAULT,
                        TOKEN_FUNGIBLE_COMMON,
                        TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES,
                        TOKEN_NON_FUNGIBLE_UNIQUE,
                        TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES,
                        SCHEDULE_CREATE_CONTRACT_CALL));

        // then:
        for (var type : SubType.class.getEnumConstants()) {
            if (type != SubType.UNRECOGNIZED) {
                assertEquals(
                        nextUsagePrices.toBuilder().setSubType(type).build(), pricesMap.get(type));
            }
        }
    }

    @Test
    void prefersNewStyleDefaultToOldStyle() {
        // setup:
        final var data = tfsWithOldAndNewPriceData(currUsagePrices, nextUsagePrices, DEFAULT);
        final Map<SubType, FeeData> pricesMap = new EnumMap<>(SubType.class);

        // when:
        subject.ensurePricesMapHasRequiredTypes(data, pricesMap, EnumSet.of(TOKEN_FUNGIBLE_COMMON));

        // then:
        assertEquals(1, pricesMap.size());
        assertEquals(
                nextUsagePrices.toBuilder().setSubType(TOKEN_FUNGIBLE_COMMON).build(),
                pricesMap.get(TOKEN_FUNGIBLE_COMMON));
    }

    @Test
    void canUseOldStyleDefault() {
        // setup:
        final var data = tfsWithOldAndNewPriceData(currUsagePrices, nextUsagePrices);
        final Map<SubType, FeeData> pricesMap = new EnumMap<>(SubType.class);

        // when:
        subject.ensurePricesMapHasRequiredTypes(data, pricesMap, EnumSet.of(TOKEN_FUNGIBLE_COMMON));

        // then:
        assertEquals(1, pricesMap.size());
        assertEquals(
                currUsagePrices.toBuilder().setSubType(TOKEN_FUNGIBLE_COMMON).build(),
                pricesMap.get(TOKEN_FUNGIBLE_COMMON));
    }

    private FeeSchedule getPreSubTypeFeeSchedule() {
        return FeeSchedule.newBuilder()
                .addTransactionFeeSchedule(untypedTransactionFeeSchedule(CryptoTransfer))
                .addTransactionFeeSchedule(untypedTransactionFeeSchedule(TokenMint))
                .addTransactionFeeSchedule(untypedTransactionFeeSchedule(TokenBurn))
                .addTransactionFeeSchedule(untypedTransactionFeeSchedule(TokenAccountWipe))
                .build();
    }

    private TransactionFeeSchedule untypedTransactionFeeSchedule(HederaFunctionality function) {
        return TransactionFeeSchedule.newBuilder()
                .setHederaFunctionality(function)
                .setFeeData(currUsagePrices)
                .build();
    }

    private TransactionFeeSchedule tfsWithOldAndNewPriceData(
            FeeData oldPrices, FeeData newPrices, SubType... newPriceTypes) {
        var tfs = TransactionFeeSchedule.newBuilder().setFeeData(oldPrices);
        for (var priceType : newPriceTypes) {
            final var typedPrices = newPrices.toBuilder().setSubType(priceType).build();
            tfs.addFees(typedPrices);
        }
        return tfs.build();
    }
}
