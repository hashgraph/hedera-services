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

import com.esaulpaugh.headlong.abi.LongType;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.Hash;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.txns.util.RandomGenerateTransitionLogic.randomNumFromBytes;

/**
 * System contract to generate random numbers. This will generate 256-bit pseudorandom number when no range is provided.
 * If a given 32-bit range is provided returns a pseudorandom 32-bit integer X belonging to [0, range).
 * The pseudorandom number is generated using n-3 record's running hash.
 */
@Singleton
public class RandomGeneratePrecompiledContract extends AbstractPrecompiledContract {
	private static final String PRECOMPILE_NAME = "RandomGenerate";
	private static final LongType WORD_DECODER = TypeFactory.create("uint32");
	//random256BitGenerator(uint256)
	static final int RANDOM_256_BIT_GENERATOR_SELECTOR = 0x267dc6a3;
	//randomNumberGeneratorInRange(uint32)
	static final int RANDOM_NUM_IN_RANGE_GENERATOR_SELECTOR = 0x85b4610c;
	public static final String RANDOM_GENERATE_PRECOMPILE_ADDRESS = "0x169";

	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<RecordsRunningHashLeaf> runningHashLeafSupplier;

	@Inject
	public RandomGeneratePrecompiledContract(final GasCalculator gasCalculator,
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<RecordsRunningHashLeaf> runningHashLeafSupplier) {
		super(PRECOMPILE_NAME, gasCalculator);
		this.dynamicProperties = dynamicProperties;
		this.runningHashLeafSupplier = runningHashLeafSupplier;
	}

	@Override
	public long gasRequirement(final Bytes bytes) {
		return dynamicProperties.randomGenerateGasCost();
	}

	@Override
	public Bytes compute(final Bytes input, final MessageFrame frame) {
		try {
			final var selector = input.getInt(0);
			return switch (selector) {
				case RANDOM_256_BIT_GENERATOR_SELECTOR -> random256BitGenerator();
				case RANDOM_NUM_IN_RANGE_GENERATOR_SELECTOR -> randomNumberGeneratorInRange(input);
				default -> null;
			};
		} catch (IndexOutOfBoundsException ignore) {
			return null; // should we ignore all exceptions and return null ?
		}
	}

	private Bytes randomNumberGeneratorInRange(final Bytes input) {
		final var range = rangeValueFrom(input);
		// if range is invalid fail here
		validateTrue(range >= 0, ResponseCodeEnum.INVALID_RANDOM_GENERATE_RANGE);

		final var randomNum = generateRandomNumberFromHash(range);
		// if hash is invalid or not present returns null
		if (randomNum == -1) {
			return null;
		}
		return padded(randomNum);
	}

	private int generateRandomNumberFromHash(final int range) {
		final var hash = getHash();
		if (hash == null) {
			return -1;
		}
		final var hashBytes = hash.getValue();
		return randomNumFromBytes(hashBytes, range);
	}

	private Bytes random256BitGenerator() {
		final var hash = getHash();
		if (hash == null) {
			return null;
		}
		final var hashBytes = hash.getValue();
		return Bytes.wrap(hashBytes, 0, 32);
	}

	private Bytes padded(final int result) {
		return Bytes32.leftPad(Bytes.ofUnsignedInt(result));
	}

	private Hash getHash() {
		try {
			return runningHashLeafSupplier.get().getNMinus3RunningHash().getFutureHash().get();
		} catch (InterruptedException | ExecutionException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private int rangeValueFrom(final Bytes input) {
		return WORD_DECODER.decode(input.slice(4).toArrayUnsafe()).intValue();
	}
}
