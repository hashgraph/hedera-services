package com.hedera.services.store.contracts.precompile;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.primitives.Longs;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;

import static com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract.PSEUDORANDOM_NUM_IN_RANGE_GENERATOR_SELECTOR;
import static com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract.PSEUDORANDOM_SEED_GENERATOR_SELECTOR;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.PRNG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PRNG_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.hyperledger.besu.datatypes.Address.ALTBN128_ADD;
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

@ExtendWith(MockitoExtension.class)
class PrngSystemPrecompiledContractTest {
	@Mock
	private MessageFrame frame;
	@Mock
	private GasCalculator gasCalculator;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private RecordsRunningHashLeaf runningHashLeaf;
	@Mock
	private SideEffectsTracker sideEffectsTracker;
	@Mock
	private ExpiringCreations creator;
	@Mock
	private SideEffectsTracker tracker;
	@Mock
	private RecordsHistorian recordsHistorian;
	@Mock
	private PrecompilePricingUtils pricingUtils;
	private Instant consensusNow = Instant.ofEpochSecond(123456789L);
	@Mock
	private LivePricesSource livePricesSource;
	@Mock
	private HederaStackedWorldStateUpdater updater;

	private PrngLogic logic;
	private PrngSystemPrecompiledContract subject;
	private Random r = new Random();

	private ExpirableTxnRecord.Builder childRecord = ExpirableTxnRecord.newBuilder();

	@BeforeEach
	void setUp() {
		logic = new PrngLogic(dynamicProperties, () -> runningHashLeaf, sideEffectsTracker);
		subject = new PrngSystemPrecompiledContract(gasCalculator, logic, creator, tracker, recordsHistorian,
				pricingUtils, () -> consensusNow, livePricesSource, dynamicProperties);
	}

	@Test
	void generatesRandom256BitNumber() throws InterruptedException {
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(new Hash(TxnUtils.randomUtf8Bytes(48)));
		final var result = subject.generatePseudoRandomData(random256BitGeneratorInput());
		assertEquals(32, result.toArray().length);
	}

	@Test
	void generatesRandomNumber() throws InterruptedException {
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(new Hash(TxnUtils.randomUtf8Bytes(48)));
		final var range = r.nextInt(0, Integer.MAX_VALUE);
		final var result = subject.generatePseudoRandomData(randomNumberGeneratorInput(range));
		final var randomNumber = result.toBigInteger().intValue();
		assertTrue(randomNumber >= 0 && randomNumber < range);
	}

	@Test
	void hasExpectedGasRequirement() {
		assertEquals(0, subject.gasRequirement(argOf(123)));

		subject.setGasRequirement(100);
		assertEquals(100, subject.gasRequirement(argOf(123)));
	}

	@Test
	void inputRangeCannotBeNegative() {
		final var input =randomNumberGeneratorInput(-10);
		assertThrows(InvalidTransactionException.class,
				() -> subject.generatePseudoRandomData(input));
	}

	@Test
	void computePrecompileForNegativeRangeFails() {
		final var input = randomNumberGeneratorInput(-10);
		initialSetUp();
		given(creator.createUnsuccessfulSyntheticRecord(any())).willReturn(childRecord);
		final var result = subject.computePrecompile(input, frame);

		assertEquals(INVALID_PRNG_RANGE.getNumber(), result.getOutput().toInt());
	}

	@Test
	void calculatesGasCorrectly() {
		given(pricingUtils.getCanonicalPriceInTinyCents(PRNG)).willReturn(100000000L);
		given(livePricesSource.currentGasPriceInTinycents(consensusNow, HederaFunctionality.ContractCall))
				.willReturn(800L);
		assertEquals(100000000L / 800L, subject.calculateGas());
	}

	@Test
	void insufficientGasThrows() {
		final var input = randomNumberGeneratorInput(10);
		initialSetUp();
		given(creator.createUnsuccessfulSyntheticRecord(any())).willReturn(childRecord);
		given(frame.getRemainingGas()).willReturn(0L);
		final var result = subject.computePrecompile(input, frame);

		assertEquals(INSUFFICIENT_GAS.getNumber(), result.getOutput().toInt());
	}

	@Test
	void happyPathWithRandomNumberGeneratedWorks() throws InterruptedException {
		final var input = randomNumberGeneratorInput(10);
		initialSetUp();
		given(creator.createSuccessfulSyntheticRecord(anyList(), any(), anyString())).willReturn(childRecord);
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(new Hash(TxnUtils.randomUtf8Bytes(48)));

		final var result = subject.computePrecompile(input, frame);

		assertEquals(SUCCESS.getNumber(), result.getOutput().toInt());
	}

	@Test
	void happyPathWithRandomSeedGeneratedWorks() throws InterruptedException {
		final var input = random256BitGeneratorInput();
		initialSetUp();
		given(creator.createSuccessfulSyntheticRecord(anyList(), any(), anyString())).willReturn(childRecord);
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(new Hash(TxnUtils.randomUtf8Bytes(48)));

		final var result = subject.computePrecompile(input, frame);

		assertEquals(SUCCESS.getNumber(), result.getOutput().toInt());
	}

	@Test
	void unknownExceptionFailsTheCall() throws InterruptedException {
		final var input = random256BitGeneratorInput();
		initialSetUp();
		given(creator.createSuccessfulSyntheticRecord(anyList(), any(), anyString())).willThrow(IndexOutOfBoundsException.class);
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(new Hash(TxnUtils.randomUtf8Bytes(48)));

		final var result = subject.computePrecompile(input, frame);

		assertEquals(FAIL_INVALID.getNumber(), result.getOutput().toInt());
	}

	@Test
	void selectorMustBeRecognized() {
		final var fragmentSelector = Bytes.of((byte) 0xab, (byte) 0xab, (byte) 0xab, (byte) 0xab);
		final var input = Bytes.concatenate(fragmentSelector, Bytes32.ZERO);
		assertNull(subject.generatePseudoRandomData(input));
	}

	@Test
	void invalidHashReturnsSentinelOutputs() throws InterruptedException {
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(new Hash(TxnUtils.randomUtf8Bytes(48)));

		var result = subject.generatePseudoRandomData(random256BitGeneratorInput());
		assertEquals(32, result.toArray().length);

		// hash is null
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(null);

		result = subject.generatePseudoRandomData(random256BitGeneratorInput());
		assertNull(result);

		final var range = r.nextInt(0, Integer.MAX_VALUE);
		result = subject.generatePseudoRandomData(randomNumberGeneratorInput(range));
		final var randomNumberBytes = result;
		assertNull(randomNumberBytes);
	}

	@Test
	void interruptedExceptionReturnsNull() throws InterruptedException {
		final var runningHash = mock(Hash.class);
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(runningHash);

		var result = subject.generatePseudoRandomData(random256BitGeneratorInput());
		assertNull(result);

		final var range = r.nextInt(0, Integer.MAX_VALUE);
		result = subject.generatePseudoRandomData(randomNumberGeneratorInput(range));
		assertNull(result);
	}

	@Test
	void childRecordHasExpectations() {
		final var randomNum = 10L;
		setUpForChildRecord();
		given(creator.createSuccessfulSyntheticRecord(anyList(), any(), anyString())).willReturn(childRecord);
		final var childRecord = subject.createSuccessfulChildRecord(Bytes.ofUnsignedInt(randomNum),
				frame, randomNumberGeneratorInput(10));

		assertNotNull(childRecord);
		assertArrayEquals(new byte[0], childRecord.getPseudoRandomBytes());
		assertEquals(randomNum, childRecord.getPseudoRandomNumber());
		assertEquals(EntityId.fromAddress(ALTBN128_ADD), childRecord.getContractCallResult().getSenderId());
		assertEquals(randomNum, Bytes.wrap(childRecord.getContractCallResult().getResult()).toInt());
		assertEquals(null, childRecord.getContractCallResult().getError());
	}

	@Test
	void childRecordHasExpectationsForRandomSeed() {
		final var randomBytes = Bytes.wrap(TxnUtils.randomUtf8Bytes(32));
		setUpForChildRecord();
		given(creator.createSuccessfulSyntheticRecord(anyList(), any(), anyString())).willReturn(childRecord);
		final var childRecord = subject.createSuccessfulChildRecord(randomBytes,
				frame, random256BitGeneratorInput());

		assertNotNull(childRecord);
		assertEquals(32, childRecord.getPseudoRandomBytes().length);
		assertEquals(-1, childRecord.getPseudoRandomNumber());
		assertEquals(EntityId.fromAddress(ALTBN128_ADD), childRecord.getContractCallResult().getSenderId());
		assertArrayEquals(randomBytes.toArray(), Bytes.wrap(childRecord.getContractCallResult().getResult()).toArray());
		assertEquals(null, childRecord.getContractCallResult().getError());
	}

	@Test
	void failedChildRecordHasExpectations() {
		setUpForChildRecord();
		given(creator.createUnsuccessfulSyntheticRecord(any())).willReturn(childRecord);
		final var childRecord = subject.createUnsuccessfulChildRecord(FAIL_INVALID,
				frame, randomNumberGeneratorInput(10));

		assertNotNull(childRecord);
		assertArrayEquals(new byte[0], childRecord.getPseudoRandomBytes());
		assertEquals(-1, childRecord.getPseudoRandomNumber());
		assertEquals(EntityId.fromAddress(ALTBN128_ADD), childRecord.getContractCallResult().getSenderId());
		assertEquals(0, Bytes.wrap(childRecord.getContractCallResult().getResult()).toInt());
		assertEquals("FAIL_INVALID", childRecord.getContractCallResult().getError());
	}

	@Test
	void parentUpdaterMissingFails() throws InterruptedException {
		final var input = randomNumberGeneratorInput(10);
		initialSetUp();
		given(updater.parentUpdater()).willReturn(Optional.empty());
		given(creator.createSuccessfulSyntheticRecord(anyList(), any(), anyString())).willReturn(childRecord);
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(new Hash(TxnUtils.randomUtf8Bytes(48)));

		final var msg = assertThrows(InvalidTransactionException.class, () -> subject.computePrecompile(input, frame));

		assertTrue(msg.getMessage().contains("PRNG precompile frame had no parent updater"));
	}

	@Test
	void testOutsideRangeValues() throws InterruptedException {
		final var input = input(PSEUDORANDOM_NUM_IN_RANGE_GENERATOR_SELECTOR,
				Bytes32.leftPad(Bytes.wrap(ByteBuffer.allocate(8).putLong(Long.MAX_VALUE).array())));
		initialSetUp();

		final var result = subject.computePrecompile(input, frame);

		assertEquals(INVALID_PRNG_RANGE.getNumber(), result.getOutput().toInt());
	}

	private static Bytes random256BitGeneratorInput() {
		return input(PSEUDORANDOM_SEED_GENERATOR_SELECTOR, Bytes.EMPTY);
	}

	private static Bytes randomNumberGeneratorInput(final int range) {
		return input(PSEUDORANDOM_NUM_IN_RANGE_GENERATOR_SELECTOR,
				Bytes32.leftPad(Bytes.wrap(ByteBuffer.allocate(4).putInt(range).array())));
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
		given(updater.permissivelyUnaliased(frame.getSenderAddress().toArray())).willReturn(
				ALTBN128_ADD.toArray());
		given(pricingUtils.getCanonicalPriceInTinyCents(PRNG)).willReturn(100000000L);
		given(livePricesSource.currentGasPriceInTinycents(consensusNow, HederaFunctionality.ContractCall))
				.willReturn(830L);
		given(frame.getRemainingGas()).willReturn(400_000L);
		given(updater.parentUpdater()).willReturn(Optional.of(updater));
	}

	private void setUpForChildRecord() {
		given(frame.getSenderAddress()).willReturn(ALTBN128_ADD);
		given(frame.getWorldUpdater()).willReturn(updater);
		given(updater.permissivelyUnaliased(frame.getSenderAddress().toArray())).willReturn(
				ALTBN128_ADD.toArray());
		given(dynamicProperties.shouldExportPrecompileResults()).willReturn(true);
		given(frame.getValue()).willReturn(Wei.of(100L));
		given(frame.getInputData()).willReturn(Bytes.EMPTY);
	}
}
