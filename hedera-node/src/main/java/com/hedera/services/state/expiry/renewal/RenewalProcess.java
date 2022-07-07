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

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.state.expiry.EntityProcessResult;
import com.hedera.services.state.expiry.removal.AccountGC;
import com.hedera.services.state.expiry.removal.ContractGC;
import com.hedera.services.utils.EntityNum;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

import static com.hedera.services.state.expiry.EntityProcessResult.DONE;
import static com.hedera.services.state.expiry.EntityProcessResult.NOTHING_TO_DO;
import static com.hedera.services.state.expiry.EntityProcessResult.STILL_MORE_TO_DO;

@Singleton
public class RenewalProcess {
	private final AccountGC accountGC;
	private final ContractGC contractGC;
	private final FeeCalculator fees;
	private final RenewalRecordsHelper recordsHelper;
	private final GlobalDynamicProperties dynamicProperties;
	private final RenewableEntityClassifier helper;

	private Instant cycleTime = null;

	@Inject
	public RenewalProcess(
			final AccountGC accountGC,
			final ContractGC contractGC,
			final FeeCalculator fees,
			final RenewableEntityClassifier helper,
			final GlobalDynamicProperties dynamicProperties,
			final RenewalRecordsHelper recordsHelper
	) {
		this.fees = fees;
		this.helper = helper;
		this.accountGC = accountGC;
		this.contractGC = contractGC;
		this.recordsHelper = recordsHelper;
		this.dynamicProperties = dynamicProperties;
	}

	public void beginRenewalCycle(final Instant currentConsTime) {
		assertNotInCycle();
		cycleTime = currentConsTime;
		recordsHelper.beginRenewalCycle();
	}

	public void endRenewalCycle() {
		assertInCycle();
		cycleTime = null;
		recordsHelper.endRenewalCycle();
	}

	public EntityProcessResult process(final long literalNum) {
		assertInCycle();

		final var longNow = cycleTime.getEpochSecond();
		final var entityNum = EntityNum.fromLong(literalNum);
		final var classification = helper.classify(entityNum, longNow);
		return switch (classification) {
			case DETACHED_ACCOUNT_GRACE_PERIOD_OVER -> removeIfTargeted(entityNum, false);
			case DETACHED_CONTRACT_GRACE_PERIOD_OVER -> removeIfTargeted(entityNum, true);
			case EXPIRED_ACCOUNT_READY_TO_RENEW -> renewIfTargeted(entityNum, false);
			case EXPIRED_CONTRACT_READY_TO_RENEW -> renewIfTargeted(entityNum, true);
			default -> NOTHING_TO_DO;
		};
	}

	private EntityProcessResult renewIfTargeted(final EntityNum entityNum, final boolean isContract) {
		if (isNotTargeted(isContract)) {
			return NOTHING_TO_DO;
		}
		final var lastClassified = helper.getLastClassified();
		final var payer = helper.resolvePayerForAutoRenew();

		final long reqPeriod = lastClassified.getAutoRenewSecs();
		final var assessment = fees.assessCryptoAutoRenewal(lastClassified, reqPeriod, cycleTime, payer);
		final long renewalPeriod = assessment.renewalPeriod();
		final long renewalFee = assessment.fee();
		helper.renewLastClassifiedWith(renewalFee, renewalPeriod);

		recordsHelper.streamCryptoRenewal(entityNum,
				renewalFee,
				lastClassified.getExpiry() + renewalPeriod,
				isContract,
				EntityNum.fromLong(payer.state().number()));
		return DONE;
	}

	private EntityProcessResult removeIfTargeted(final EntityNum contractNum, final boolean isContract) {
		if (isNotTargeted(isContract)) {
			return NOTHING_TO_DO;
		}
		final var lastClassified = helper.getLastClassified();
		if (isContract && !contractGC.expireBestEffort(contractNum, lastClassified)) {
			return STILL_MORE_TO_DO;
		}
		final var treasuryReturns = accountGC.expireBestEffort(contractNum, lastClassified);
		recordsHelper.streamCryptoRemoval(contractNum, treasuryReturns.tokenTypes(), treasuryReturns.transfers());
		return treasuryReturns.finished() ? DONE : STILL_MORE_TO_DO;
	}

	private boolean isNotTargeted(final boolean isContract) {
		return isContract ? !dynamicProperties.shouldAutoRenewContracts() :
				!dynamicProperties.shouldAutoRenewAccounts();
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

	@VisibleForTesting
	Instant getCycleTime() {
		return cycleTime;
	}
}
