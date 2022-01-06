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
import com.hedera.services.grpc.marshalling.AliasResolver;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
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

import static com.hedera.services.grpc.marshalling.AliasResolver.usesAliases;
import static com.hedera.services.utils.MiscUtils.scheduledFunctionOf;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;

public class DeterministicThrottling implements TimedFunctionalityThrottling {
	private static final Logger log = LogManager.getLogger(DeterministicThrottling.class);
	private static final ThrottleReqOpsScaleFactor ONE_TO_ONE_SCALE = ThrottleReqOpsScaleFactor.from("1:1");

	private final IntSupplier capacitySplitSource;
	private final AliasManager aliasManager;
	private final GlobalDynamicProperties dynamicProperties;

	private List<DeterministicThrottle> activeThrottles = Collections.emptyList();
	private EnumMap<HederaFunctionality, ThrottleReqsManager> functionReqs = new EnumMap<>(HederaFunctionality.class);

	public DeterministicThrottling(
			final IntSupplier capacitySplitSource,
			final AliasManager aliasManager,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.capacitySplitSource = capacitySplitSource;
		this.dynamicProperties = dynamicProperties;
		this.aliasManager = aliasManager;
	}

	@Override
	public boolean shouldThrottleTxn(TxnAccessor accessor) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean shouldThrottleQuery(HederaFunctionality queryFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean shouldThrottleTxn(TxnAccessor accessor, Instant now) {
		final var function = accessor.getFunction();
		ThrottleReqsManager manager;
		if ((manager = functionReqs.get(function)) == null) {
			return true;
		} else if (function == TokenMint) {
			return shouldThrottleMint(manager, accessor.getTxn().getTokenMint(), now);
		} else if (function == CryptoTransfer) {
			if (dynamicProperties.isAutoCreationEnabled()) {
				if (!accessor.areAutoCreationsCounted()) {
					accessor.countAutoCreationsWith(aliasManager);
				}
				return shouldThrottleTransfer(manager, accessor.getNumAutoCreations(), now);
			} else {
				/* Since auto-creation is disabled, if this transfer does attempt one, it will
				resolve to NOT_SUPPORTED right away; so we don't want to ask for capacity from the
				CryptoCreate throttle bucket. */
				return !manager.allReqsMetAt(now);
			}
		} else if (function == ScheduleCreate) {
			final var scheduled = accessor.getTxn().getScheduleCreate().getScheduledTransactionBody();
			return shouldThrottleScheduleCreate(manager, scheduled, now);
		} else {
			return !manager.allReqsMetAt(now);
		}
	}

	@Override
	public boolean shouldThrottleQuery(HederaFunctionality queryFunction, Instant now) {
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

	void setFunctionReqs(EnumMap<HederaFunctionality, ThrottleReqsManager> functionReqs) {
		this.functionReqs = functionReqs;
	}

	private boolean shouldThrottleScheduleCreate(
			final ThrottleReqsManager manager,
			final SchedulableTransactionBody scheduled,
			final Instant now
	) {
		final var scheduledFunction = scheduledFunctionOf(scheduled);
		if (dynamicProperties.isAutoCreationEnabled() && scheduledFunction == CryptoTransfer) {
			final var txn = scheduled.getCryptoTransfer();
			if (usesAliases(txn)) {
				final var resolver = new AliasResolver();
				resolver.resolve(txn, aliasManager);
				final var numAutoCreations = resolver.perceivedAutoCreations();
				if (numAutoCreations > 0) {
					return shouldThrottleAutoCreations(numAutoCreations, now);
				}
			}
		}
		return !manager.allReqsMetAt(now);
	}

	private boolean shouldThrottleTransfer(
			final ThrottleReqsManager manager,
			final int numAutoCreations,
			final Instant now
	) {
		return (numAutoCreations == 0)
				? !manager.allReqsMetAt(now)
				: shouldThrottleAutoCreations(numAutoCreations, now);
	}

	private boolean shouldThrottleAutoCreations(final int n, final Instant now) {
		final var manager = functionReqs.get(CryptoCreate);
		return manager == null || !manager.allReqsMetAt(now, n, ONE_TO_ONE_SCALE);
	}


	private boolean shouldThrottleMint(ThrottleReqsManager manager, TokenMintTransactionBody op, Instant now) {
		final var numNfts = op.getMetadataCount();
		if (numNfts == 0) {
			return !manager.allReqsMetAt(now);
		} else {
			return !manager.allReqsMetAt(now, numNfts, dynamicProperties.nftMintScaleFactor());
		}
	}
}
