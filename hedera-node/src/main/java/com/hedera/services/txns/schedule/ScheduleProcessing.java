package com.hedera.services.txns.schedule;
/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiPredicate;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.keys.CharacteristicsFactory;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.logic.SigsAndPayerKeyScreen;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.throttling.TimedFunctionalityThrottling;
import com.hedera.services.throttling.annotations.ScheduleThrottle;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.swirlds.common.system.transaction.SwirldTransaction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.utils.EntityNum.fromScheduleId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_FUTURE_THROTTLE_EXCEEDED;

/**
 * Class that encapsulates some of the more complex processing of scheduled transactions.
 */
@Singleton
public class ScheduleProcessing {
	private static final Logger log = LogManager.getLogger(ScheduleProcessing.class);

	private final SigImpactHistorian sigImpactHistorian;
	private final ScheduleStore store;
	private final ScheduleExecutor scheduleExecutor;
	private final GlobalDynamicProperties dynamicProperties;
	private final SigsAndPayerKeyScreen sigsAndPayerKeyScreen;
	private final CharacteristicsFactory characteristics;
	private final TimedFunctionalityThrottling scheduleThrottling;

	SigMapScheduleClassifier classifier = new SigMapScheduleClassifier();
	SignatoryUtils.ScheduledSigningsWitness signingsWitness = SignatoryUtils::witnessScoped;
	BiPredicate<ScheduleVirtualValue, InHandleActivationHelper> isReady = SignatoryUtils::isReady;

	@Inject
	public ScheduleProcessing(final SigImpactHistorian sigImpactHistorian, final ScheduleStore store,
			final ScheduleExecutor scheduleExecutor, final GlobalDynamicProperties dynamicProperties,
			final SigsAndPayerKeyScreen sigsAndPayerKeyScreen, final CharacteristicsFactory characteristics,
			@ScheduleThrottle final TimedFunctionalityThrottling scheduleThrottling) {
		this.sigImpactHistorian = sigImpactHistorian;
		this.store = store;
		this.scheduleExecutor = scheduleExecutor;
		this.dynamicProperties = dynamicProperties;
		this.sigsAndPayerKeyScreen = sigsAndPayerKeyScreen;
		this.characteristics = characteristics;
		this.scheduleThrottling = scheduleThrottling;
	}

	/**
	 * Expires all scheduled transactions, that are in a final state, having an expiry before consensusTime,
	 * and having an expiry before any transaction that is ready to execute.
	 */
	public void expire(Instant consensusTime) {

		while (true) {
			store.advanceCurrentMinSecond(consensusTime);

			List<ScheduleID> txnIdsToExpire = store.nextSchedulesToExpire(consensusTime);
			if (txnIdsToExpire.isEmpty()) {
				break;
			}

			for (var txnId : txnIdsToExpire) {
				store.expire(txnId);
				sigImpactHistorian.markEntityChanged(fromScheduleId(txnId).longValue());
			}
		}

	}

	/**
	 * Gets the next scheduled transaction that is available to execute. Scheduled transactions may be expired
	 * as needed during this call.
	 * @param consensusTime the current consensus time
	 * @param previous the previous accessor returned from this method, if available.
	 * @param onlyExpire true if we are only expiring and not trying to execute anything.
	 * @return the TxnAccessor of the next scheduled transaction to execute, or null if there are none.
	 */
	@Nullable
	public TxnAccessor triggerNextTransactionExpiringAsNeeded(Instant consensusTime,
			@Nullable TxnAccessor previous, boolean onlyExpire) {

		var previousId = previous == null ? null : previous.getScheduleRef();

		while (true) {

			expire(consensusTime);

			var next = store.nextScheduleToEvaluate(consensusTime);

			if (next == null) {
				return null;
			}

			if (next.equals(previousId)) {
				log.error("tried to process the same transaction twice! {}", next);
				throw new IllegalStateException("tried to process the same transaction twice!");
			}

			previousId = next;

			// if we were going to check throttling to prevent scheduled transactions from all executing
			// rapidly after downtime, it would be done here.

			try {

				if (!this.dynamicProperties.schedulingLongTermEnabled()) {
					// if long term is disabled, we always expire transactions that would otherwise
					// execute autonomously
					store.expire(next);
					sigImpactHistorian.markEntityChanged(fromScheduleId(next).longValue());
					continue;
				}

				var schedule = store.get(next);

				var parentSignedTxn = schedule.parentAsSignedTxn();

				var accessor = PlatformTxnAccessor.from(
						SignedTxnAccessor.from(parentSignedTxn.toByteArray()),
						new SwirldTransaction(parentSignedTxn.toByteArray()));

				sigsAndPayerKeyScreen.applyTo(accessor, Optional.empty());

				var inHandleActivationHelper = new InHandleActivationHelper(characteristics, () -> accessor);

				if (!isReady.test(schedule, inHandleActivationHelper)) {

					// expire transactions that are not ready to execute
					store.expire(next);
					sigImpactHistorian.markEntityChanged(fromScheduleId(next).longValue());

				} else if (onlyExpire) {

					// if we are only expiring, we have to stop processing here and return null
					return null;

				} else {

					var triggerResult = scheduleExecutor.getTriggeredTxnAccessor(next, store, false);

					if (triggerResult.getLeft() != OK) {
						log.error("Scheduled transaction was not trigger-able even though it should be! Expiring it. {}",
								next);
						store.expire(next);
						sigImpactHistorian.markEntityChanged(fromScheduleId(next).longValue());
					} else {
						return triggerResult.getRight();
					}

				}
			} catch (Exception e) {
				log.error("SCHEDULED TRANSACTION SKIPPED!! Failed to triggered transaction due unexpected error! {}",
						next, e);

				// Immediately expire malfunctioning transactions, if we get here then there is a bug.
				// We can't leave it in the db, it will prevent other scheduled transactions from processing.
				store.expire(next);
				sigImpactHistorian.markEntityChanged(fromScheduleId(next).longValue());
			}

		}

	}

	/**
	 * @param schedule a schedule to check the "future throttles" for.
	 * @return an error code if there was an error, OK otherwise
	 */
	public ResponseCodeEnum checkFutureThrottlesForCreate(final ScheduleVirtualValue schedule) {

		if (dynamicProperties.schedulingLongTermEnabled()) {
			scheduleThrottling.resetUsage();

			final TreeMap<RichInstant, List<TxnAccessor>> transactions = new TreeMap<>();

			var curSecond = schedule.calculatedExpirationTime().getSeconds();

			var bySecond = store.getBySecond(curSecond);

			if (bySecond != null) {
				bySecond.getIds().values().forEach(ids -> ids.forEach(id -> {
					var existing = store.getNoError(EntityNum.fromLong(id).toGrpcScheduleId());

					if (existing != null) {
						if (existing.calculatedExpirationTime().getSeconds() != curSecond) {
							log.error("bySecond contained a schedule in the wrong spot! Ignoring it! spot={}, id={}, schedule={}",
									curSecond, id, existing);
						} else {
							var list = transactions.computeIfAbsent(existing.calculatedExpirationTime(),
									k -> new ArrayList<>());
							list.add(SignedTxnAccessor.uncheckedFrom(existing.asSignedTxn()));
						}
					} else {
						log.error("bySecond contained a schedule that does not exist! Ignoring it! second={}, id={}",
								curSecond, id);
					}
				}));
			}

			var list = transactions.computeIfAbsent(schedule.calculatedExpirationTime(), k -> new ArrayList<>());
			list.add(SignedTxnAccessor.uncheckedFrom(schedule.asSignedTxn()));

			for (var entry : transactions.entrySet()) {
				for (var t : entry.getValue()) {
					if (scheduleThrottling.shouldThrottleTxn(t, entry.getKey().toJava())) {
						if (scheduleThrottling.wasLastTxnGasThrottled()) {
							return SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED;
						} else {
							return SCHEDULE_FUTURE_THROTTLE_EXCEEDED;
						}
					}
				}
			}

		}

		return OK;
	}

}
