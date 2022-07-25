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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract.PSEUDORANDOM_SEED_GENERATOR_SELECTOR;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.PRNG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static org.hyperledger.besu.datatypes.Address.ALTBN128_ADD;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INVALID_OPERATION;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.common.primitives.Longs;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.HederaBlockValues;
import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.txns.util.PrngLogic;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.utility.CommonUtils;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrngSystemPrecompiledContractTest {
    private static final Hash WELL_KNOWN_HASH =
            new Hash(
                    CommonUtils.unhex(
                            "65386630386164632d356537632d343964342d623437372d62636134346538386338373133633038316162372d616300"));
    @Mock private MessageFrame frame;
    @Mock private GasCalculator gasCalculator;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private RecordsRunningHashLeaf runningHashLeaf;
    @Mock private SideEffectsTracker sideEffectsTracker;
    @Mock private ExpiringCreations creator;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private PrecompilePricingUtils pricingUtils;
    private final Instant consensusNow = Instant.ofEpochSecond(123456789L);
    @Mock private LivePricesSource livePricesSource;
    @Mock private HederaStackedWorldStateUpdater updater;

    private PrngSystemPrecompiledContract subject;
    private final Random r = new Random();

    private final ExpirableTxnRecord.Builder childRecord = ExpirableTxnRecord.newBuilder();

    @BeforeEach
    void setUp() {
        final var logic =
                new PrngLogic(dynamicProperties, () -> runningHashLeaf, sideEffectsTracker);
        subject =
                new PrngSystemPrecompiledContract(
                        gasCalculator,
                        logic,
                        creator,
                        recordsHistorian,
                        pricingUtils,
                        livePricesSource,
                        dynamicProperties);
    }

    @Test
    void generatesRandom256BitNumber() throws InterruptedException {
        given(runningHashLeaf.nMinusThreeRunningHash())
                .willReturn(new Hash(TxnUtils.randomUtf8Bytes(48)));
        final var result = subject.generatePseudoRandomData(random256BitGeneratorInput());
        assertEquals(32, result.toArray().length);
    }

    @Test
    void hasExpectedGasRequirement() {
        assertEquals(0, subject.gasRequirement(argOf(123)));

        subject.setGasRequirement(100);
        assertEquals(100, subject.gasRequirement(argOf(123)));
    }

    @Test
    void calculatesGasCorrectly() {
        given(pricingUtils.getCanonicalPriceInTinyCents(PRNG)).willReturn(100000000L);
        given(
                        livePricesSource.currentGasPriceInTinycents(
                                consensusNow, HederaFunctionality.ContractCall))
                .willReturn(800L);
        assertEquals(100000000L / 800L, subject.calculateGas(consensusNow));
    }

    @Test
    void insufficientGasThrows() {
        final var input = random256BitGeneratorInput();
        initialSetUp();
        given(creator.createUnsuccessfulSyntheticRecord(any())).willReturn(childRecord);
        given(frame.getRemainingGas()).willReturn(0L);
        given(frame.getBlockValues()).willReturn(new HederaBlockValues(10L, 123L, consensusNow));

        final var response = subject.computePrngResult(10L, input, frame);
        assertEquals(INVALID_OPERATION, response.getLeft().getHaltReason().get());
        assertEquals(INSUFFICIENT_GAS, response.getRight());

        final var result = subject.computePrecompile(input, frame);
        assertNull(result.getOutput());
    }

    @Test
    void happyPathWithRandomSeedGeneratedWorks() throws InterruptedException {
        final ArgumentCaptor<SideEffectsTracker> captor =
                ArgumentCaptor.forClass(SideEffectsTracker.class);

        final var input = random256BitGeneratorInput();
        initialSetUp();
        given(creator.createSuccessfulSyntheticRecord(anyList(), captor.capture(), anyString()))
                .willReturn(childRecord);
        given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(WELL_KNOWN_HASH);
        given(frame.getBlockValues()).willReturn(new HederaBlockValues(10L, 123L, consensusNow));

        final var response = subject.computePrngResult(10L, input, frame);
        assertEquals(Optional.empty(), response.getLeft().getHaltReason());
        assertEquals(COMPLETED_SUCCESS, response.getLeft().getState());
        assertNull(response.getRight());

        final var result = subject.computePrecompile(input, frame);
        assertNotNull(result.getOutput());

        // and:
        final var effectsTracker = captor.getValue();
        assertArrayEquals(
                Arrays.copyOfRange(WELL_KNOWN_HASH.getValue(), 0, 32),
                effectsTracker.getPseudorandomBytes());
    }

    @Test
    void unknownExceptionFailsTheCall() {
        final var input = random256BitGeneratorInput();
        initialSetUp();
        given(frame.getBlockValues()).willReturn(new HederaBlockValues(10L, 123L, consensusNow));
        final var logic = mock(PrngLogic.class);
        subject =
                new PrngSystemPrecompiledContract(
                        gasCalculator,
                        logic,
                        creator,
                        recordsHistorian,
                        pricingUtils,
                        livePricesSource,
                        dynamicProperties);
        given(logic.getNMinus3RunningHashBytes()).willThrow(IndexOutOfBoundsException.class);

        final var response = subject.computePrngResult(10L, input, frame);
        assertEquals(INVALID_OPERATION, response.getLeft().getHaltReason().get());

        final var result = subject.computePrecompile(input, frame);
        assertNull(result.getOutput());
    }

    @Test
    void selectorMustBeRecognized() {
        final var fragmentSelector = Bytes.of((byte) 0xab, (byte) 0xab, (byte) 0xab, (byte) 0xab);
        final var input = Bytes.concatenate(fragmentSelector, Bytes32.ZERO);
        assertNull(subject.generatePseudoRandomData(input));
    }

    @Test
    void invalidHashReturnsSentinelOutputs() throws InterruptedException {
        given(runningHashLeaf.nMinusThreeRunningHash())
                .willReturn(new Hash(TxnUtils.randomUtf8Bytes(48)));

        var result = subject.generatePseudoRandomData(random256BitGeneratorInput());
        assertEquals(32, result.toArray().length);

        // hash is null
        given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(null);

        result = subject.generatePseudoRandomData(random256BitGeneratorInput());
        assertNull(result);
    }

    @Test
    void interruptedExceptionReturnsNull() throws InterruptedException {
        final var runningHash = mock(Hash.class);
        given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(runningHash);

        var result = subject.generatePseudoRandomData(random256BitGeneratorInput());
        assertNull(result);
    }

    @Test
    void childRecordHasExpectations() {
        final var randomNum = 10L;
        setUpForChildRecord();
        given(creator.createSuccessfulSyntheticRecord(anyList(), any(), anyString()))
                .willReturn(childRecord);
        var childRecord =
                subject.createSuccessfulChildRecord(
                        Bytes.ofUnsignedInt(randomNum), frame, random256BitGeneratorInput());

        assertNotNull(childRecord);
        assertEquals(
                EntityId.fromAddress(ALTBN128_ADD),
                childRecord.getContractCallResult().getSenderId());
        assertEquals(
                randomNum, Bytes.wrap(childRecord.getContractCallResult().getResult()).toInt());
        assertNull(childRecord.getContractCallResult().getError());

        childRecord =
                subject.createSuccessfulChildRecord(
                        Bytes.ofUnsignedInt(randomNum), frame, defaultInput());

        assertNotNull(childRecord);
        assertEquals(
                EntityId.fromAddress(ALTBN128_ADD),
                childRecord.getContractCallResult().getSenderId());
        assertEquals(
                randomNum, Bytes.wrap(childRecord.getContractCallResult().getResult()).toInt());
        assertNull(childRecord.getContractCallResult().getError());
    }

    @Test
    void childRecordHasExpectationsForRandomSeed() {
        final var randomBytes = Bytes.wrap(TxnUtils.randomUtf8Bytes(32));

        setUpForChildRecord();
        given(creator.createSuccessfulSyntheticRecord(anyList(), any(), anyString()))
                .willReturn(childRecord);
        final var childRecord =
                subject.createSuccessfulChildRecord(
                        randomBytes, frame, random256BitGeneratorInput());

        assertNotNull(childRecord);
        assertEquals(
                EntityId.fromAddress(ALTBN128_ADD),
                childRecord.getContractCallResult().getSenderId());
        assertArrayEquals(
                randomBytes.toArray(),
                Bytes.wrap(childRecord.getContractCallResult().getResult()).toArray());
        assertNull(childRecord.getContractCallResult().getError());
    }

    @Test
    void failedChildRecordHasExpectations() {
        setUpForChildRecord();
        given(creator.createUnsuccessfulSyntheticRecord(any())).willReturn(childRecord);
        final var childRecord = subject.createUnsuccessfulChildRecord(FAIL_INVALID, frame);

        assertNotNull(childRecord);
        assertArrayEquals(new byte[0], childRecord.getPseudoRandomBytes());
        assertEquals(-1, childRecord.getPseudoRandomNumber());
        assertEquals(
                EntityId.fromAddress(ALTBN128_ADD),
                childRecord.getContractCallResult().getSenderId());
        assertEquals(0, Bytes.wrap(childRecord.getContractCallResult().getResult()).toInt());
        assertEquals("FAIL_INVALID", childRecord.getContractCallResult().getError());
    }

    @Test
    void parentUpdaterMissingFails() throws InterruptedException {
        final var input = random256BitGeneratorInput();
        initialSetUp();
        given(updater.parentUpdater()).willReturn(Optional.empty());
        given(creator.createSuccessfulSyntheticRecord(anyList(), any(), anyString()))
                .willReturn(childRecord);
        given(frame.getBlockValues()).willReturn(new HederaBlockValues(10L, 123L, consensusNow));
        given(runningHashLeaf.nMinusThreeRunningHash())
                .willReturn(new Hash(TxnUtils.randomUtf8Bytes(48)));

        final var msg =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> subject.computePrecompile(input, frame));

        assertTrue(msg.getMessage().contains("PRNG precompile frame had no parent updater"));
    }

    private static Bytes random256BitGeneratorInput() {
        return input(PSEUDORANDOM_SEED_GENERATOR_SELECTOR, Bytes.EMPTY);
    }

    private static Bytes defaultInput() {
        return input(0x34676789, Bytes.EMPTY);
    }

    private static Bytes input(final int selector, final Bytes wordInput) {
        return Bytes.concatenate(Bytes.ofUnsignedInt(selector & 0xffffffffL), wordInput);
    }

    private static Bytes argOf(final long amount) {
        return Bytes.wrap(Longs.toByteArray(amount));
    }

    private void initialSetUp() {
        given(frame.getSenderAddress()).willReturn(ALTBN128_ADD);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.permissivelyUnaliased(frame.getSenderAddress().toArray()))
                .willReturn(ALTBN128_ADD.toArray());
        given(pricingUtils.getCanonicalPriceInTinyCents(PRNG)).willReturn(100000000L);
        given(
                        livePricesSource.currentGasPriceInTinycents(
                                consensusNow, HederaFunctionality.ContractCall))
                .willReturn(830L);
        given(frame.getRemainingGas()).willReturn(400_000L);
        given(updater.parentUpdater()).willReturn(Optional.of(updater));
    }

    private void setUpForChildRecord() {
        given(frame.getSenderAddress()).willReturn(ALTBN128_ADD);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.permissivelyUnaliased(frame.getSenderAddress().toArray()))
                .willReturn(ALTBN128_ADD.toArray());
        given(dynamicProperties.shouldExportPrecompileResults()).willReturn(true);
        given(frame.getValue()).willReturn(Wei.of(100L));
        given(frame.getInputData()).willReturn(Bytes.EMPTY);
    }
}
