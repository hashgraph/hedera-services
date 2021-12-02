package com.hedera.services.throttling;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.function.IntSupplier;

import static com.hedera.services.utils.MiscUtils.isGasThrottled;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;

public class DeterministicThrottling implements TimedFunctionalityThrottling {
	private static final Logger log = LogManager.getLogger(DeterministicThrottling.class);

	private static final String GAS_THROTTLE_AT_ZERO_WARNING_TPL =
			"{} gas throttling enabled, but limited to 0 gas/sec";

	private final IntSupplier capacitySplitSource;
	private final GlobalDynamicProperties dynamicProperties;

	private List<DeterministicThrottle> activeThrottles = Collections.emptyList();
	private EnumMap<HederaFunctionality, ThrottleReqsManager> functionReqs = new EnumMap<>(HederaFunctionality.class);

	private boolean consensusThrottled;
	private boolean lastTxnWasGasThrottled;
	private GasLimitDeterministicThrottle gasThrottle;

	public DeterministicThrottling(
			final IntSupplier capacitySplitSource,
			final GlobalDynamicProperties dynamicProperties,
			final boolean consensusThrottled
	) {
		this.capacitySplitSource = capacitySplitSource;
		this.dynamicProperties = dynamicProperties;
		this.consensusThrottled = consensusThrottled;
	}

	@Override
	public boolean shouldThrottleTxn(TxnAccessor accessor) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean shouldThrottleQuery(HederaFunctionality queryFunction, Query query) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean shouldThrottleTxn(final TxnAccessor accessor, final Instant now) {
		lastTxnWasGasThrottled = false;

		if (isGasExhausted(accessor, now)) {
			lastTxnWasGasThrottled = true;
			return true;
		}

		final var function = accessor.getFunction();
		ThrottleReqsManager manager;

		if ((manager = functionReqs.get(function)) == null) {
			return true;
		} else if (function == CryptoTransfer) {
			return shouldThrottleTransfer(manager, accessor.getAutoAccountCreationsCount(), now);
		} else if (function == TokenMint) {
			return shouldThrottleMint(manager, accessor.getTxn().getTokenMint(), now);
		} else {
			return !manager.allReqsMetAt(now);
		}
	}

	private boolean shouldThrottleTransfer(
			final ThrottleReqsManager manager, final int autoAccountCreationCount, final Instant now) {
		if (autoAccountCreationCount == 0) {
			return !manager.allReqsMetAt(now);
		} else {
			return !functionReqs.get(CryptoCreate).allReqsMetAt(
					now, autoAccountCreationCount, ThrottleReqOpsScaleFactor.from("1:1"));
		}
	}

	@Override
	public boolean wasLastTxnGasThrottled() {
		return lastTxnWasGasThrottled;
	}

	@Override
	public void leakUnusedGasPreviouslyReserved(long value) {
		gasThrottle.leakUnusedGasPreviouslyReserved(value);
	}

	@Override
	public boolean shouldThrottleQuery(HederaFunctionality queryFunction, Instant now, Query query) {
		if (isGasThrottled(queryFunction) &&
				dynamicProperties.shouldThrottleByGas() &&
				(gasThrottle == null || !gasThrottle.allow(now, query.getContractCallLocal().getGas()))) {
			return true;
		}
		ThrottleReqsManager manager;
		if ((manager = functionReqs.get(queryFunction)) == null) {
			return true;
		}
		return !manager.allReqsMetAt(now);
	}

	@Override
	public List<DeterministicThrottle> allActiveThrottles() {
		return activeThrottles;
	}

	@Override
	public List<DeterministicThrottle> activeThrottlesFor(HederaFunctionality function) {
		ThrottleReqsManager manager;
		if ((manager = functionReqs.get(function)) == null) {
			return Collections.emptyList();
		}
		return manager.managedThrottles();
	}

	@Override
	public void rebuildFor(ThrottleDefinitions defs) {
		List<DeterministicThrottle> newActiveThrottles = new ArrayList<>();
		EnumMap<HederaFunctionality, List<Pair<DeterministicThrottle, Integer>>> reqLists
				= new EnumMap<>(HederaFunctionality.class);

		int n = capacitySplitSource.getAsInt();
		for (var bucket : defs.getBuckets()) {
			try {
				var mapping = bucket.asThrottleMapping(n);
				var throttle = mapping.getLeft();
				var reqs = mapping.getRight();
				for (var req : reqs) {
					reqLists.computeIfAbsent(req.getLeft(), ignore -> new ArrayList<>())
							.add(Pair.of(throttle, req.getRight()));
				}
				newActiveThrottles.add(throttle);
			} catch (IllegalStateException badBucket) {
				log.error("When constructing bucket '{}' from state: {}", bucket.getName(), badBucket.getMessage());
			}
		}
		EnumMap<HederaFunctionality, ThrottleReqsManager> newFunctionReqs = new EnumMap<>(HederaFunctionality.class);
		reqLists.forEach((function, reqs) -> newFunctionReqs.put(function, new ThrottleReqsManager(reqs)));

		functionReqs = newFunctionReqs;
		activeThrottles = newActiveThrottles;
		logResolvedDefinitions();
	}

	@Override
	public void applyGasConfig() {
		final var n = capacitySplitSource.getAsInt();
		long splitCapacity;
		if (consensusThrottled) {
			if (dynamicProperties.shouldThrottleByGas() && dynamicProperties.consensusThrottleGasLimit() == 0) {
				log.warn(GAS_THROTTLE_AT_ZERO_WARNING_TPL, "Consensus");
				return;
			} else {
				splitCapacity = dynamicProperties.consensusThrottleGasLimit() / n;
			}
		} else {
			if (dynamicProperties.shouldThrottleByGas() && dynamicProperties.frontendThrottleGasLimit() == 0) {
				log.warn(GAS_THROTTLE_AT_ZERO_WARNING_TPL, "Frontend");
				return;
			} else {
				splitCapacity = dynamicProperties.frontendThrottleGasLimit() / n;
			}
		}
		gasThrottle = new GasLimitDeterministicThrottle(splitCapacity);
		final var configDesc = "Resolved " +
				(consensusThrottled ? "consensus" : "frontend") +
				" gas throttle (after splitting capacity " + n + " ways) -\n  " +
				gasThrottle.getCapacity() +
				" gas/sec (throttling " +
				(dynamicProperties.shouldThrottleByGas() ? "ON" : "OFF") +
				")";
		log.info(configDesc);
	}

	@Override
	public GasLimitDeterministicThrottle gasLimitThrottle() {
		return gasThrottle;
	}

	private void logResolvedDefinitions() {
		int n = capacitySplitSource.getAsInt();
		var sb = new StringBuilder("Resolved throttles (after splitting capacity " + n + " ways) - \n");
		functionReqs.entrySet().stream()
				.sorted(Comparator.comparing(entry -> entry.getKey().toString()))
				.forEach(entry -> {
					var function = entry.getKey();
					var manager = entry.getValue();
					sb.append("  ").append(function).append(": ")
							.append(manager.asReadableRequirements())
							.append("\n");
				});
		log.info(sb.toString().trim());
	}

	private boolean shouldThrottleMint(ThrottleReqsManager manager, TokenMintTransactionBody op, Instant now) {
		final var numNfts = op.getMetadataCount();
		if (numNfts == 0) {
			return !manager.allReqsMetAt(now);
		} else {
			return !manager.allReqsMetAt(now, numNfts, dynamicProperties.nftMintScaleFactor());
		}
	}

	private boolean isGasExhausted(final TxnAccessor accessor, final Instant now) {
		return dynamicProperties.shouldThrottleByGas() &&
				isGasThrottled(accessor.getFunction()) &&
				(gasThrottle == null || !gasThrottle.allow(now, accessor.getGasLimitForContractTx()));
	}

	/* --- Only used by unit tests --- */
	void setConsensusThrottled(boolean consensusThrottled) {
		this.consensusThrottled = consensusThrottled;
	}

	void setFunctionReqs(EnumMap<HederaFunctionality, ThrottleReqsManager> functionReqs) {
		this.functionReqs = functionReqs;
	}
}
