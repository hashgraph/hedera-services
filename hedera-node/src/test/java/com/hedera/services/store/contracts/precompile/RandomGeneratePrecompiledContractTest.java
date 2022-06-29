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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.test.utils.TxnUtils;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.Random;

import static com.hedera.services.store.contracts.precompile.RandomGeneratePrecompiledContract.RANDOM_256_BIT_GENERATOR_SELECTOR;
import static com.hedera.services.store.contracts.precompile.RandomGeneratePrecompiledContract.RANDOM_NUM_IN_RANGE_GENERATOR_SELECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RANDOM_GENERATE_RANGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RandomGeneratePrecompiledContractTest {
	private static final long GAS_REQUIREMENT = 10000L;
	@Mock
	private MessageFrame frame;
	@Mock
	private GasCalculator gasCalculator;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private RecordsRunningHashLeaf runningHashLeaf;
	private RandomGeneratePrecompiledContract subject;
	private Random r = new Random();

	@BeforeEach
	void setUp() {
		subject = new RandomGeneratePrecompiledContract(gasCalculator, dynamicProperties,
				() -> runningHashLeaf);
	}

	@Test
	void generatesRandom256BitNumber() {
		given(runningHashLeaf.getNMinus3RunningHash()).willReturn(
				new RunningHash(new Hash(TxnUtils.randomUtf8Bytes(48))));
		final var result = subject.compute(random256BitGeneratorInput(), frame);
		assertEquals(32, result.toArray().length);
	}

	@Test
	void generatesRandomNumber() {
		given(runningHashLeaf.getNMinus3RunningHash()).willReturn(
				new RunningHash(new Hash(TxnUtils.randomUtf8Bytes(48))));

		final var range = r.nextInt(0, Integer.MAX_VALUE);
		final var result = subject.compute(randomNumberGeneratorInput(range), frame);
		final var randomNumber = result.toBigInteger().intValue();
		assertTrue(randomNumber >= 0 && randomNumber < range);
	}

	@Test
	void hasExpectedGasRequirement() {
		given(dynamicProperties.randomGenerateGasCost()).willReturn(GAS_REQUIREMENT);
		assertEquals(GAS_REQUIREMENT, subject.gasRequirement(argOf(123)));
	}

	@Test
	void inputRangeCannotBeNegative() {
		final var input = randomNumberGeneratorNegativeInput(-10);
		final var ex = assertThrows(InvalidTransactionException.class, () -> subject.compute(input, frame));
		assertEquals(INVALID_RANDOM_GENERATE_RANGE, ex.getResponseCode());
	}

	@Test
	void selectorMustBeFullyPresent() {
		final var fragmentSelector = Bytes.of(0xab);
		assertNull(subject.compute(fragmentSelector, frame));
	}

	@Test
	void selectorMustBeRecognized() {
		final var fragmentSelector = Bytes.of((byte) 0xab, (byte) 0xab, (byte) 0xab, (byte) 0xab);
		final var input = Bytes.concatenate(fragmentSelector, Bytes32.ZERO);
		assertNull(subject.compute(input, frame));
	}

	@Test
	void invalidHashReturnsSentinelOutputs() {
		given(runningHashLeaf.getNMinus3RunningHash()).willReturn(
				new RunningHash(new Hash()));
		var result = subject.compute(random256BitGeneratorInput(), frame);
		assertEquals(32, result.toArray().length);

		given(runningHashLeaf.getNMinus3RunningHash()).willReturn(
				new RunningHash(null));
		result = subject.compute(random256BitGeneratorInput(), frame);
		assertEquals(null, result);

		final var range = r.nextInt(0, Integer.MAX_VALUE);
		result = subject.compute(randomNumberGeneratorInput(range), frame);
		final var randomNumberBytes = result;
		assertEquals(null, randomNumberBytes);
	}

	private static Bytes random256BitGeneratorInput() {
		return input(RANDOM_256_BIT_GENERATOR_SELECTOR, Bytes.EMPTY);
	}

	private static Bytes randomNumberGeneratorInput(final int range) {
		return input(RANDOM_NUM_IN_RANGE_GENERATOR_SELECTOR, Bytes32.leftPad(Bytes.ofUnsignedInt(range)));
	}

	private static Bytes randomNumberGeneratorNegativeInput(final int range) {
		return input(RANDOM_NUM_IN_RANGE_GENERATOR_SELECTOR, Bytes32.leftPad(
				Bytes.wrap(ByteBuffer.allocate(4).putInt(range).array())));
	}

	private static Bytes input(final int selector, final Bytes wordInput) {
		return Bytes.concatenate(Bytes.ofUnsignedInt(selector & 0xffffffffL), wordInput);
	}

	private static Bytes argOf(final long amount) {
		return Bytes.wrap(Longs.toByteArray(amount));
	}

}
