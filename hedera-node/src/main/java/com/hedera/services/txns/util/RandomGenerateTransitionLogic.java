package com.hedera.services.txns.util;

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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.Hash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.ByteBuffer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RANDOM_GENERATE_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Provides the state transition for generating a pseudorandom bytes or pseudorandom number.
 */
@Singleton
public class RandomGenerateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(RandomGenerateTransitionLogic.class);

	private final TransactionContext txnCtx;
	private final SideEffectsTracker sideEffectsTracker;
	private final Supplier<RecordsRunningHashLeaf> runningHashLeafSupplier;

	private final GlobalDynamicProperties properties;

	@Inject
	public RandomGenerateTransitionLogic(final TransactionContext txnCtx,
			final SideEffectsTracker sideEffectsTracker,
			final Supplier<RecordsRunningHashLeaf> runningHashLeafSupplier,
			final GlobalDynamicProperties properties) {
		this.txnCtx = txnCtx;
		this.sideEffectsTracker = sideEffectsTracker;
		this.runningHashLeafSupplier = runningHashLeafSupplier;
		this.properties = properties;
	}

	@Override
	public void doStateTransition() {
		if (!properties.isRandomGenerationEnabled()) {
			return;
		}
		final var op = txnCtx.accessor().getTxn().getRandomGenerate();

		Hash nMinus3RunningHash;
		try {
			// Use n-3 running hash instead of n-1 running hash for processing transactions quickly
			nMinus3RunningHash = runningHashLeafSupplier.get().nMinusThreeRunningHash();
		} catch (InterruptedException e) {
			log.error("Interrupted when computing n-3 running hash", e);
			Thread.currentThread().interrupt();
			return;
		}

		if (nMinus3RunningHash == null) {
			log.info("No n-3 record running hash available to generate random number");
			return;
		}

		//generate binary string from the running hash of records
		final var pseudoRandomBytes = nMinus3RunningHash.getValue();
		final var range = op.getRange();
		if (range > 0) {
			// generate pseudorandom number in the given range
			final var initialBitsValue = Math.abs(ByteBuffer.wrap(pseudoRandomBytes, 0, 4).getInt());
			int pseudoRandomNumber = (int) ((range * (long) initialBitsValue) >>> 32);
			sideEffectsTracker.trackRandomNumber(pseudoRandomNumber);
		} else {
			sideEffectsTracker.trackRandomBytes(pseudoRandomBytes);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasRandomGenerate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	private ResponseCodeEnum validate(final TransactionBody randomGenerateTxn) {
		final var range = randomGenerateTxn.getRandomGenerate().getRange();
		if (range < 0) {
			return INVALID_RANDOM_GENERATE_RANGE;
		}
		return OK;
	}
}
