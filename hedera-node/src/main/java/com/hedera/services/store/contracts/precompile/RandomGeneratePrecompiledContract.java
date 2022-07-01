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
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.txns.util.RandomGenerateLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;

/**
 * System contract to generate random numbers. This will generate 256-bit pseudorandom number when no range is provided.
 * If a given 32-bit range is provided returns a pseudorandom 32-bit integer X belonging to [0, range).
 * The pseudorandom number is generated using n-3 record's running hash.
 */
@Singleton
public class RandomGeneratePrecompiledContract extends AbstractPrecompiledContract {
	private static final Logger log = LogManager.getLogger(RandomGeneratePrecompiledContract.class);

	private static final String PRECOMPILE_NAME = "RandomGenerate";
	private static final LongType WORD_DECODER = TypeFactory.create("uint32");
	//random256BitGenerator(uint256)
	static final int RANDOM_256_BIT_GENERATOR_SELECTOR = 0x267dc6a3;
	//randomNumberGeneratorInRange(uint32)
	static final int RANDOM_NUM_IN_RANGE_GENERATOR_SELECTOR = 0x85b4610c;
	public static final String RANDOM_GENERATE_PRECOMPILE_ADDRESS = "0x169";

	private final RandomGenerateLogic randomGenerateLogic;
	private final GlobalDynamicProperties properties;

	@Inject
	public RandomGeneratePrecompiledContract(final GasCalculator gasCalculator,
			final RandomGenerateLogic randomGenerateLogic,
			final GlobalDynamicProperties properties) {
		super(PRECOMPILE_NAME, gasCalculator);
		this.randomGenerateLogic = randomGenerateLogic;
		this.properties = properties;
	}

	@Override
	public long gasRequirement(final Bytes bytes) {
		return properties.randomGenerateGasCost();
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
		} catch (InvalidTransactionException e) {
			frame.setRevertReason(EncodingFacade.resultFrom(e.getResponseCode()));
		} catch (Exception e) {
			frame.setRevertReason(EncodingFacade.resultFrom(ResponseCodeEnum.FAIL_INVALID));
			frame.setState(REVERT);
			log.warn("Internal precompile failure", e);
		}
		return null;
	}

	private Bytes randomNumberGeneratorInRange(final Bytes input) {
		final var range = rangeValueFrom(input);
		validateTrue(range >= 0, ResponseCodeEnum.INVALID_RANDOM_GENERATE_RANGE);

		final var hashBytes = randomGenerateLogic.getNMinus3RunningHashBytes();
		if (hashBytes == null) {
			return null;
		}

		final var randomNum = randomGenerateLogic.randomNumFromBytes(hashBytes, range);
		return padded(randomNum);
	}

	private Bytes random256BitGenerator() {
		final var hashBytes = randomGenerateLogic.getNMinus3RunningHashBytes();
		if (hashBytes == null) {
			return null;
		}
		return Bytes.wrap(hashBytes, 0, 32);
	}

	private Bytes padded(final int result) {
		return Bytes32.leftPad(Bytes.ofUnsignedInt(result));
	}

	private int rangeValueFrom(final Bytes input) {
		return WORD_DECODER.decode(input.slice(4).toArrayUnsafe()).intValue();
	}
}
