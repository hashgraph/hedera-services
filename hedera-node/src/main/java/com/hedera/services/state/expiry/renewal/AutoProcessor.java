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

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.state.merkle.MerkleEntityId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.EnumSet;

import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DELETED_TOKEN;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.EXPIRED_TOKEN;

public class AutoProcessor {
	private static final Logger log = LogManager.getLogger(AutoProcessor.class);

	private static final EnumSet<ExpiredEntityClassification> TERMINAL_CLASSIFICATIONS = EnumSet.of(
			DETACHED_ACCOUNT_GRACE_PERIOD_OVER,
			DELETED_TOKEN,
			EXPIRED_TOKEN
	);

	private final long shard, realm;

	private final FeeCalculator fees;
	private final RenewalHelper helper;
	private final RenewalRecordsHelper recordsHelper;

	
	private Instant cycleTime = null;
	
	public AutoProcessor(
			FeeCalculator fees,
			HederaNumbers hederaNums,
			RenewalHelper helper,
			RenewalRecordsHelper recordsHelper
	) {
		this.fees = fees;
		this.helper = helper;
		this.recordsHelper = recordsHelper;

		this.realm = hederaNums.realm();
		this.shard = hederaNums.shard();
	}

	public void beginRenewalCycle(Instant now) {
		assertNotInCycle();

		cycleTime = now;
		recordsHelper.beginRenewalCycle(now);
	}

	public boolean process(long entityNum) {
		assertInCycle();

		final var longNow = cycleTime.getEpochSecond();
		final var classification = helper.classify(entityNum, longNow);
		if (TERMINAL_CLASSIFICATIONS.contains(classification)) {
			log.debug("Terminal classification entity num {} ({})", entityNum, classification);
		}
		switch (classification) {
			case OTHER:
			case DETACHED_ACCOUNT:
			case DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN:
				break;
			case DETACHED_ACCOUNT_GRACE_PERIOD_OVER:
				processDetachedAccountGracePeriodOver(new MerkleEntityId(shard, realm, entityNum));
				return true;
			case EXPIRED_ACCOUNT_READY_TO_RENEW:
				processExpiredAccountReadyToRenew(new MerkleEntityId(shard, realm, entityNum));
				return true;
		}
		return false;
	}

	private void processExpiredAccountReadyToRenew(MerkleEntityId accountId) {
		final var lastClassified = helper.getLastClassifiedAccount();
		final long reqPeriod = lastClassified.getAutoRenewSecs();
		final var usageAssessment = fees.assessCryptoAutoRenewal(lastClassified, reqPeriod, cycleTime);
		final long effPeriod = usageAssessment.renewalPeriod();
		final long renewalFee = usageAssessment.fee();

		helper.renewLastClassifiedWith(renewalFee, effPeriod);
		recordsHelper.streamCryptoRenewal(accountId, renewalFee, lastClassified.getExpiry() + effPeriod);
	}

	private void processDetachedAccountGracePeriodOver(MerkleEntityId accountId) {
		final var tokensDisplaced = helper.removeLastClassifiedAccount();
		recordsHelper.streamCryptoRemoval(accountId, tokensDisplaced.getLeft(), tokensDisplaced.getRight());
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
			throw new IllegalStateException("Cannot start renewal cycle, already started!");
		}
	}

	Instant getCycleTime() {
		return cycleTime;
	}
}
