package com.hedera.services.state.expiry.renewal;

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

import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.state.expiry.EntityProcessResult;
import com.hedera.services.utils.EntityNum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.EnumSet;

import static com.hedera.services.state.expiry.EntityProcessResult.DONE;
import static com.hedera.services.state.expiry.EntityProcessResult.NOTHING_TO_DO;
import static com.hedera.services.state.expiry.EntityProcessResult.STILL_MORE_TO_DO;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_ACCOUNT_GRACE_PERIOD_OVER;

@Singleton
public class RenewalProcess {
	private static final Logger log = LogManager.getLogger(RenewalProcess.class);

	private static final EnumSet<RenewableEntityType> TERMINAL_CLASSIFICATIONS = EnumSet.of(
			DETACHED_ACCOUNT_GRACE_PERIOD_OVER
	);

	private final AccountGC accountGC;
	private final FeeCalculator fees;
	private final RenewableEntityClassifier helper;
	private final RenewalRecordsHelper recordsHelper;

	private Instant cycleTime = null;

	@Inject
	public RenewalProcess(
			final AccountGC accountGC,
			final FeeCalculator fees,
			final RenewableEntityClassifier helper,
			final RenewalRecordsHelper recordsHelper
	) {
		this.fees = fees;
		this.helper = helper;
		this.accountGC = accountGC;
		this.recordsHelper = recordsHelper;
	}

	public void beginRenewalCycle(Instant now) {
		assertNotInCycle();

		cycleTime = now;
		recordsHelper.beginRenewalCycle(now);
	}

	public EntityProcessResult process(final long entityNum) {
		assertInCycle();

		final var longNow = cycleTime.getEpochSecond();
		final var entityKey = EntityNum.fromLong(entityNum);
		final var classification = helper.classify(entityKey, longNow);
		if (TERMINAL_CLASSIFICATIONS.contains(classification)) {
			log.debug("Terminal classification entity num {} ({})", entityNum, classification);
		}
		return switch (classification) {
			case DETACHED_ACCOUNT_GRACE_PERIOD_OVER -> expireAccount(entityKey);
			case EXPIRED_ACCOUNT_READY_TO_RENEW -> renewAccount(entityKey);
			default -> NOTHING_TO_DO;
		};
	}

	private EntityProcessResult renewAccount(EntityNum accountId) {
		final var lastClassified = helper.getLastClassifiedAccount();
		final long reqPeriod = lastClassified.getAutoRenewSecs();
		final var usageAssessment = fees.assessCryptoAutoRenewal(lastClassified, reqPeriod, cycleTime);
		final long effPeriod = usageAssessment.renewalPeriod();
		final long renewalFee = usageAssessment.fee();

		helper.renewLastClassifiedWith(renewalFee, effPeriod);
		recordsHelper.streamCryptoRenewal(accountId, renewalFee, lastClassified.getExpiry() + effPeriod);
		return DONE;
	}

	private EntityProcessResult expireAccount(EntityNum num) {
		final var treasuryReturns = accountGC.expireBestEffort(num, helper.getLastClassifiedAccount());
		recordsHelper.streamCryptoRemoval(num, treasuryReturns.tokenTypes(), treasuryReturns.transfers());
		return treasuryReturns.finished() ? DONE : STILL_MORE_TO_DO;
	}

	public void endRenewalCycle() {
		assertInCycle();

		cycleTime = null;
		recordsHelper.endRenewalCycle();
	}

	private void assertInCycle() {
		if (cycleTime == null) {
			throw new IllegalStateException("Cannot stream records if not in a renewal cycle!");
		}
	}

	private void assertNotInCycle() {
		if (cycleTime != null) {
			throw new IllegalStateException("Cannot end renewal cycle, none is started!");
		}
	}

	Instant getCycleTime() {
		return cycleTime;
	}
}
