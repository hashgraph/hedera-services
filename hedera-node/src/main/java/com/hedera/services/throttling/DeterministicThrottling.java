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
import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hedera.services.grpc.marshalling.AliasResolver;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.sysfiles.domain.throttling.ThrottleBucket;
import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.sysfiles.domain.throttling.ThrottleGroup;
import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.IntSupplier;

import static com.hedera.services.utils.MiscUtils.isGasThrottled;
import static com.hedera.services.grpc.marshalling.AliasResolver.usesAliases;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;

public class DeterministicThrottling implements TimedFunctionalityThrottling {
	private static final Logger log = LogManager.getLogger(DeterministicThrottling.class);
	private static final ThrottleReqOpsScaleFactor ONE_TO_ONE_SCALE = ThrottleReqOpsScaleFactor.from("1:1");

	private static final String GAS_THROTTLE_AT_ZERO_WARNING_TPL =
			"{} gas throttling enabled, but limited to 0 gas/sec";

	public enum DeterministicThrottlingMode { HAPI, CONSENSUS, SCHEDULE }

	private final IntSupplier capacitySplitSource;
	private final AliasManager aliasManager;
	private final GlobalDynamicProperties dynamicProperties;
	private final ScheduleStore scheduleStore;

	private List<DeterministicThrottle> activeThrottles = Collections.emptyList();
	private EnumMap<HederaFunctionality, ThrottleReqsManager> functionReqs = new EnumMap<>(HederaFunctionality.class);
	private ThrottleDefinitions activeDefs = null;

	private DeterministicThrottlingMode mode;
	private boolean lastTxnWasGasThrottled;
	private GasLimitDeterministicThrottle gasThrottle;

	public DeterministicThrottling(
			final IntSupplier capacitySplitSource,
			final AliasManager aliasManager,
			final GlobalDynamicProperties dynamicProperties,
			final DeterministicThrottlingMode mode,
			final ScheduleStore scheduleStore) {
		this.capacitySplitSource = capacitySplitSource;
		this.dynamicProperties = dynamicProperties;
		this.mode = mode;
		this.aliasManager = aliasManager;
		this.scheduleStore = scheduleStore;
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
		resetLastAllowedUse();
		lastTxnWasGasThrottled = false;
		if (shouldThrottleTxn(false, accessor, accessor.getTxn(), accessor.getFunction(), now)) {
			reclaimLastAllowedUse();
			return true;
		}
		return false;
	}

	@Override
	public boolean wasLastTxnGasThrottled() {
		return lastTxnWasGasThrottled;
	}

	@Override
	public void leakUnusedGasPreviouslyReserved(TxnAccessor accessor, long value) {
		if (accessor.throttleExempt()) {
			return;
		}
		gasThrottle.leakUnusedGasPreviouslyReserved(value);
	}

	@Override
	public boolean shouldThrottleQuery(HederaFunctionality queryFunction, Instant now, Query query) {
		resetLastAllowedUse();
		if (isGasThrottled(queryFunction) &&
				dynamicProperties.shouldThrottleByGas() &&
				(gasThrottle == null || !gasThrottle.allow(now, query.getContractCallLocal().getGas()))) {
			reclaimLastAllowedUse();
			return true;
		}
		ThrottleReqsManager manager;
		if ((manager = functionReqs.get(queryFunction)) == null) {
			reclaimLastAllowedUse();
			return true;
		}
		if(!manager.allReqsMetAt(now)) {
			reclaimLastAllowedUse();
			return true;
		}
		return false;
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

		if (mode == DeterministicThrottlingMode.SCHEDULE) {
			calculateScheduleThrottles(defs);
		} else {
			calculateThrottles(defs, capacitySplitSource.getAsInt());
		}

		logResolvedDefinitions();
	}

	@Override
	public void applyGasConfig() {
		long capacity;

		switch (mode) {
			case CONSENSUS:
				if (dynamicProperties.shouldThrottleByGas() && dynamicProperties.consensusThrottleGasLimit() == 0) {
					log.warn(GAS_THROTTLE_AT_ZERO_WARNING_TPL, "Consensus");
					return;
				} else {
					capacity = dynamicProperties.consensusThrottleGasLimit();
				}
				break;
			case HAPI:
				if (dynamicProperties.shouldThrottleByGas() && dynamicProperties.frontendThrottleGasLimit() == 0) {
					log.warn(GAS_THROTTLE_AT_ZERO_WARNING_TPL, "Frontend");
					return;
				} else {
					capacity = dynamicProperties.frontendThrottleGasLimit();
				}
				break;
			case SCHEDULE:
				if (dynamicProperties.shouldThrottleByGas() && dynamicProperties.scheduleThrottleMaxGasLimit() == 0) {
					log.warn(GAS_THROTTLE_AT_ZERO_WARNING_TPL, "Schedule");
					return;
				} else {
					capacity = dynamicProperties.scheduleThrottleMaxGasLimit();
				}
				break;
			default: throw new IllegalStateException("unknown mode " + mode);
		}

		gasThrottle = new GasLimitDeterministicThrottle(capacity);

		final var configDesc = "Resolved " +
				mode +
				" gas throttle -\n  " +
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

	@Override
	public void resetUsage() {
		lastTxnWasGasThrottled = false;
		activeThrottles.forEach(DeterministicThrottle::resetUsage);
		if (gasThrottle != null) {
			gasThrottle.resetUsage();
		}
	}

	private void logResolvedDefinitions() {
		int n = capacitySplitSource.getAsInt();
		var sb = new StringBuilder("Resolved throttles for " + mode + " (after splitting capacity " + n + " ways) - \n");
		functionReqs.entrySet().stream()
				.sorted(Comparator.comparing(entry -> entry.getKey().toString()))
				.forEach(entry -> {
					var function = entry.getKey();
					var manager = entry.getValue();
					sb.append("  ").append(function).append(": ")
							.append(manager.asReadableRequirements())
							.append("\n");
				});
		log.info("{}", () -> sb.toString().trim());
	}

	private boolean shouldThrottleTxn(boolean isChild, final TxnAccessor accessor, final TransactionBody txn,
			final HederaFunctionality function, final Instant now) {

		if (accessor.throttleExempt()) {
			return false;
		}

		if (isGasExhausted(txn, function, now)) {
			lastTxnWasGasThrottled = true;
			return true;
		}

		if (function == ScheduleCreate) {
			if (isChild) {
				throw new IllegalStateException("ScheduleCreate cannot be a child!");
			}
			return shouldThrottleScheduleCreate(accessor, txn, now);
		} else if (function == ScheduleSign) {
			if (isChild) {
				throw new IllegalStateException("ScheduleSign cannot be a child!");
			}
			return shouldThrottleScheduleSign(accessor, txn, now);
		} else {

			ThrottleReqsManager manager;

			if ((manager = functionReqs.get(function)) == null) {
				return true;
			} else if (function == TokenMint) {
				return shouldThrottleMint(manager, txn.getTokenMint(), now);
			} else if (function == CryptoTransfer) {
				if (dynamicProperties.isAutoCreationEnabled()) {

					int numAutoCreations;
					if (isChild) {
						final var resolver = new AliasResolver();
						resolver.resolve(txn.getCryptoTransfer(), aliasManager);
						numAutoCreations = resolver.perceivedAutoCreations();
					} else {
						if (!accessor.areAutoCreationsCounted()) {
							accessor.countAutoCreationsWith(aliasManager);
						}
						numAutoCreations = accessor.getNumAutoCreations();
					}

					return shouldThrottleTransfer(manager, numAutoCreations, now);
				} else {
					/* Since auto-creation is disabled, if this transfer does attempt one, it will
					resolve to NOT_SUPPORTED right away; so we don't want to ask for capacity from the
					CryptoCreate throttle bucket. */
					return !manager.allReqsMetAt(now);
				}
			} else {
				return !manager.allReqsMetAt(now);
			}
		}
	}

	private boolean shouldThrottleScheduleCreate(
			final TxnAccessor accessor,
			final TransactionBody txn,
			final Instant now
	) {

		final var scheduleCreate = txn.getScheduleCreate();
		final var scheduled = scheduleCreate.getScheduledTransactionBody();

		final var normalTxn = MiscUtils.asOrdinary(scheduled);

		HederaFunctionality scheduledFunction;
		try {
			scheduledFunction = MiscUtils.functionOf(normalTxn);
		} catch (UnknownHederaFunctionality ex) {
			log.error("ScheduleCreate was associated with an invalid txn.", ex);
			return true;
		}

		var manager = functionReqs.get(ScheduleCreate);

		// maintain legacy behaviour
		if (!dynamicProperties.schedulingLongTermEnabled()) {
			if (dynamicProperties.isAutoCreationEnabled() && scheduledFunction == CryptoTransfer) {
				final var xfer = scheduled.getCryptoTransfer();
				if (usesAliases(xfer)) {
					final var resolver = new AliasResolver();
					resolver.resolve(xfer, aliasManager);
					final var numAutoCreations = resolver.perceivedAutoCreations();
					if (numAutoCreations > 0) {
						return shouldThrottleAutoCreations(numAutoCreations, now);
					}
				}
			}
			return !manager.allReqsMetAt(now);
		}

		if (!manager.allReqsMetAt(now)) {
			return true;
		}

		// deeply check throttle at the hapi level if the schedule could immediately execute
		if ((!scheduleCreate.getWaitForExpiry()) && (mode == DeterministicThrottlingMode.HAPI)) {
			return shouldThrottleTxn(true, accessor, normalTxn, scheduledFunction, now);
		}

		return false;
	}

	private boolean shouldThrottleScheduleSign(
			final TxnAccessor accessor,
			final TransactionBody txn,
			final Instant now
	) {

		var manager = functionReqs.get(ScheduleSign);

		if (!manager.allReqsMetAt(now)) {
			return true;
		}

		// maintain legacy behaviour
		if (!dynamicProperties.schedulingLongTermEnabled()) {
			return false;
		}

		// deeply check throttle only at the hapi level
		if (mode != DeterministicThrottlingMode.HAPI) {
			return false;
		}

		final var scheduledId = txn.getScheduleSign().getScheduleID();

		var scheduleValue = scheduleStore.getNoError(scheduledId);
		if (scheduleValue == null) {
			log.error("Tried to throttle a ScheduleSign at the HAPI level that does not exist! We should not get here.");
			return true;
		}

		// only check deeply if the schedule could immediately execute
		if (scheduleValue.isWaitForExpiry()) {
			return false;
		}

		final var normalTxn = scheduleValue.ordinaryViewOfScheduledTxn();

		HederaFunctionality scheduledFunction;
		try {
			scheduledFunction = MiscUtils.functionOf(normalTxn);
		} catch (UnknownHederaFunctionality ex) {
			log.error("ScheduleSign was associated with an invalid txn.", ex);
			return true;
		}

		return shouldThrottleTxn(true, accessor, normalTxn, scheduledFunction, now);
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

	private boolean isGasExhausted(final TransactionBody txn, final HederaFunctionality function, final Instant now) {
		return dynamicProperties.shouldThrottleByGas() &&
				isGasThrottled(function) &&
				(gasThrottle == null || !gasThrottle.allow(now, MiscUtils.getGasLimitForContractTx(txn, function)));
	}

	private void reclaimLastAllowedUse() {
		activeThrottles.forEach(DeterministicThrottle::reclaimLastAllowedUse);
		if (gasThrottle != null) {
			gasThrottle.reclaimLastAllowedUse();
		}
	}

	private void resetLastAllowedUse() {
		activeThrottles.forEach(DeterministicThrottle::resetLastAllowedUse);
		if (gasThrottle != null) {
			gasThrottle.resetLastAllowedUse();
		}
	}

	private void calculateThrottles(ThrottleDefinitions defs, long capacitySplit) {
		List<DeterministicThrottle> newActiveThrottles = new ArrayList<>();
		EnumMap<HederaFunctionality, List<Pair<DeterministicThrottle, Integer>>> reqLists
				= new EnumMap<>(HederaFunctionality.class);

		for (var bucket : defs.getBuckets()) {
			try {
				var mapping = bucket.asThrottleMapping(capacitySplit);
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
		activeDefs = defs;
	}

	@NotNull
	private void calculateScheduleThrottles(final ThrottleDefinitions defs) {

		// copy the throttles
		var defsCopy = ThrottleDefinitions.fromProto(defs.toProto());

		// get the throttle groups with the minimum tps value for each transaction type,
		// these are the effective max tps for each type of transaction
		final EnumMap<HederaFunctionality, ThrottleGroup> minMtps = new EnumMap<>(HederaFunctionality.class);

		for (var bucket : defsCopy.getBuckets()) {
			for (var group : bucket.getThrottleGroups()) {
				for (var op : group.getOperations()) {
					// remove functions that can't be scheduled
					if (MiscUtils.isSchedulable(op)) {
						ThrottleGroup min = minMtps.get(op);
						if (min == null || min.impliedMilliOpsPerSec() > group.impliedMilliOpsPerSec()) {
							minMtps.put(op, group);
						}
					}
				}
			}
		}

		// dedup the min tps groups
		final IdentityHashMap<ThrottleGroup, ThrottleBucket> groups = new IdentityHashMap<>();
		for (var grp : minMtps.values()) {
			groups.put(grp, null);
		}

		// filter out throttle groups we no longer need, set burst periods to 1, and rename
		for (var bucket : defsCopy.getBuckets()) {
			bucket.setThrottleGroups(bucket.getThrottleGroups().stream().filter(g -> {
				if (groups.containsKey(g)) {
					groups.put(g, bucket);
					return true;
				}
				return false;
			}).toList());
			bucket.setBurstPeriod(1);
			bucket.setBurstPeriodMs(1000);
		}

		defsCopy.setBuckets(defsCopy.getBuckets().stream().filter(b -> !b.getThrottleGroups().isEmpty()).toList());

		long maxTps = dynamicProperties.schedulingMaxTxnPerSecond();

		// if it's impossible to scale the throttles, throw exception
		if (maxTps < groups.size()) {
			throw new IllegalStateException("Cannot fit throttles into max scheduled transactions! "
					+ maxTps + " < " + groups.size());
		}

		// find the divisor that scales the throttles such that the max tps is equal to or less than the max schedule tps
		// and all transaction groups have at least one tps.

		long maxMtps = maxTps * 1_000;

		long left = 1;
		long right = Long.MAX_VALUE - 1;
		long scheduleCapacitySplit;
		while (true) {

			long m = (left + right) / 2L;
			long sum = 0L;

			for (var grp : groups.keySet()) {
				long toAdd = (grp.impliedMilliOpsPerSec() + m - 1L) / m;
				// we round to the second to try to avoid the lcm stuff in ThrottleBucket causing overflows
				toAdd = (toAdd / 1000) * 1000;
				if (toAdd < 1000) {
					toAdd = 1000;
				}
				sum += toAdd;
			}
			if (sum > maxMtps) {
				left = m + 1L;
			} else {
				right = m;
			}

			if (left >= right) {
				scheduleCapacitySplit = left;
				break;
			}
		}

		// scale all the throttles

		var sum = 0L;
		for (var grp : groups.keySet()) {
			long toSet = (grp.impliedMilliOpsPerSec() + scheduleCapacitySplit - 1L) / scheduleCapacitySplit;
			toSet = (toSet / 1000) * 1000;
			if (toSet <	1000) {
				toSet = 1000;
			}
			grp.setOpsPerSec((int) (toSet / 1000));
			grp.setMilliOpsPerSec(toSet);
			sum += grp.impliedMilliOpsPerSec();
		}

		var sb = new StringBuilder("Schedule Throttles: ");
		for (var e : minMtps.entrySet().stream().sorted(Comparator.comparing(a -> a.getKey().toString())).toList()) {
			sb.append("\n")
					.append(groups.get(e.getValue()) != null ? groups.get(e.getValue()).getName() : null)
					.append(" : ")
					.append(e.getKey())
					.append(" - ")
					.append(e.getValue().impliedMilliOpsPerSec() / 1000)
					.append(" tps");
		}
		log.info(sb);


		if (sum > maxMtps) {
			throw new IllegalStateException("Could not scale throttles!");
		}

		calculateThrottles(defsCopy, capacitySplitSource.getAsInt());
	}

	/* --- Only used by unit tests --- */
	void setMode(DeterministicThrottlingMode mode) {
		this.mode = mode;
	}

	ThrottleDefinitions getActiveDefs() {
		return this.activeDefs;
	}

	void setFunctionReqs(EnumMap<HederaFunctionality, ThrottleReqsManager> functionReqs) {
		this.functionReqs = functionReqs;
	}
}
