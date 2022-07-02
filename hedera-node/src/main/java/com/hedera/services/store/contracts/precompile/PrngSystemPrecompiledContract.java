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
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txns.util.PrngLogic;
import com.hederahashgraph.api.proto.java.PrngTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.HTS_PRECOMPILE_MIRROR_ENTITY_ID;
import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.SUCCESS_RESULT;
import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.resultFrom;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.RANDOM_GENERATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PRNG_RANGE;

/**
 * System contract to generate random numbers. This will generate 256-bit pseudorandom number when no range is provided.
 * If a given 32-bit range is provided returns a pseudorandom 32-bit integer X belonging to [0, range).
 * The pseudorandom number is generated using n-3 record's running hash.
 */
@Singleton
public class PrngSystemPrecompiledContract extends AbstractPrecompiledContract {
	private static final Logger log = LogManager.getLogger(PrngSystemPrecompiledContract.class);
	private static final String PRECOMPILE_NAME = "PRNG";
	private static final LongType WORD_DECODER = TypeFactory.create("uint32");
	//random256BitGenerator(uint256)
	static final int RANDOM_256_BIT_GENERATOR_SELECTOR = 0x267dc6a3;
	//randomNumberGeneratorInRange(uint32)
	static final int RANDOM_NUM_IN_RANGE_GENERATOR_SELECTOR = 0x85b4610c;
	public static final String PRNG_PRECOMPILE_ADDRESS = "0x169";
	private final PrngLogic prngLogic;
	private final SideEffectsTracker tracker;
	private final EntityCreator creator;
	private HederaStackedWorldStateUpdater updater;
	private final RecordsHistorian recordsHistorian;
	private final PrecompilePricingUtils pricingUtils;
	private final Supplier<Instant> consensusNow;
	private DecodingFacade decodingFacade;

	private long gasRequirement;
	private TransactionBody.Builder transactionBody;

	@Inject
	public PrngSystemPrecompiledContract(
			final GasCalculator gasCalculator,
			final PrngLogic prngLogic,
			final ExpiringCreations creator,
			final SideEffectsTracker tracker,
			final RecordsHistorian recordsHistorian,
			final PrecompilePricingUtils pricingUtils,
			final Supplier<Instant> consensusNow,
			final DecodingFacade decodingFacade) {
		super(PRECOMPILE_NAME, gasCalculator);
		this.prngLogic = prngLogic;
		this.creator = creator;
		this.tracker = tracker;
		this.recordsHistorian = recordsHistorian;
		this.pricingUtils = pricingUtils;
		this.consensusNow = consensusNow;
		this.decodingFacade = decodingFacade;
	}

	@Override
	public long gasRequirement(final Bytes bytes) {
		return gasRequirement;
	}

	@Override
	public PrecompileContractResult computePrecompile(final Bytes input, final MessageFrame frame) {
		ExpirableTxnRecord.Builder childRecord;
		Bytes result;

		try {
			gasRequirement = pricingUtils.getMinimumPriceInTinybars(RANDOM_GENERATE,
					Timestamp.newBuilder().setSeconds(consensusNow.get().getEpochSecond()).build());
			validateTrue(frame.getRemainingGas() >= gasRequirement, INSUFFICIENT_GAS);

			final var selector = input.getInt(0);
			final var randomNum = switch (selector) {
				case RANDOM_256_BIT_GENERATOR_SELECTOR -> random256BitGenerator();
				case RANDOM_NUM_IN_RANGE_GENERATOR_SELECTOR -> randomNumberGeneratorInRange(input);
				default -> null;
			};

			childRecord = createSuccessfulChildRecord(randomNum, frame);
			result = SUCCESS_RESULT;

			transactionBody = body(randomNum);
		} catch (InvalidTransactionException e) {
			childRecord = createUnsuccessfulChildRecord(e.getResponseCode(), frame, e.isReverting(),
					e.getRevertReason());
			result = resultFrom(e.getResponseCode());
		} catch (Exception e) {
			log.warn("Internal precompile failure", e);
			childRecord = createUnsuccessfulChildRecord(FAIL_INVALID, frame, false, Bytes.EMPTY);
			result = resultFrom(FAIL_INVALID);
		}

		final var parentUpdater = updater.parentUpdater();
		if (parentUpdater.isPresent()) {
			final var parent = (AbstractLedgerWorldUpdater) parentUpdater.get();
			parent.manageInProgressRecord(recordsHistorian, childRecord, transactionBody);
		} else {
			throw new InvalidTransactionException("HTS precompile frame had no parent updater", FAIL_INVALID);
		}

		return result == null ?
				PrecompiledContract.PrecompileContractResult.halt(null, Optional.of(ExceptionalHaltReason.NONE))
				: PrecompiledContract.PrecompileContractResult.success(result);
	}

	private ExpirableTxnRecord.Builder createUnsuccessfulChildRecord(
			final ResponseCodeEnum status,
			final MessageFrame frame,
			final boolean reverting,
			final Bytes revertReason) {
		final var childRecord = creator.createUnsuccessfulSyntheticRecord(status);
		addContractCallResultToRecord(childRecord, null, Optional.of(status), frame);
		if (reverting) {
			frame.setState(MessageFrame.State.REVERT);
			frame.setRevertReason(revertReason);
		}
		return childRecord;
	}

	private ExpirableTxnRecord.Builder createSuccessfulChildRecord(final Bytes randomNum, final MessageFrame frame) {
		final var childRecord = creator.createSuccessfulSyntheticRecord(
				Collections.emptyList(), tracker, EMPTY_MEMO);
		addContractCallResultToRecord(childRecord, randomNum, Optional.empty(), frame);
		return childRecord;
	}

	public TransactionBody.Builder body(final Bytes randomNum) {
		this.transactionBody = TransactionBody.newBuilder();
		if (randomNum == null) {
			log.warn("Internal precompile failure");
			transactionBody = null;
		}
		final var body = PrngTransactionBody.newBuilder();
		if (randomNum.toArray().length == 32) {
			body.setRange(randomNum.toInt());
		}
		return TransactionBody.newBuilder().setPrng(body);
	}

	private Bytes randomNumberGeneratorInRange(final Bytes input) {
		final var range = rangeValueFrom(input);
		validateTrue(range >= 0, INVALID_PRNG_RANGE);

		final var hashBytes = prngLogic.getNMinus3RunningHashBytes();
		if (hashBytes == null) {
			return null;
		}

		final var randomNum = prngLogic.randomNumFromBytes(hashBytes, range);
		return padded(randomNum);
	}

	private Bytes random256BitGenerator() {
		final var hashBytes = prngLogic.getNMinus3RunningHashBytes();
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

	private void addContractCallResultToRecord(
			final ExpirableTxnRecord.Builder childRecord,
			final Bytes result,
			final Optional<ResponseCodeEnum> errorStatus,
			final MessageFrame frame
	) {
		updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
		final var unaliasedSenderAddress = updater.permissivelyUnaliased(frame.getSenderAddress().toArray());
		final var senderAddress = Address.wrap(Bytes.of(unaliasedSenderAddress));

		final var evmFnResult = new EvmFnResult(
				HTS_PRECOMPILE_MIRROR_ENTITY_ID,
				result != null ? result.toArrayUnsafe() : EvmFnResult.EMPTY,
				errorStatus.map(ResponseCodeEnum::name).orElse(null),
				EvmFnResult.EMPTY,
				gasRequirement,
				Collections.emptyList(),
				Collections.emptyList(),
				EvmFnResult.EMPTY,
				Collections.emptyMap(),
				0L,
				0L,
				EvmFnResult.EMPTY,
				EntityId.fromAddress(senderAddress));
		childRecord.setContractCallResult(evmFnResult);
	}
}
