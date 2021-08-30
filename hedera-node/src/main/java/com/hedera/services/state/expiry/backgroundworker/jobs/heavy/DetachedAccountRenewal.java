/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.state.expiry.backgroundworker.jobs.heavy;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.state.expiry.backgroundworker.jobs.Job;
import com.hedera.services.state.expiry.backgroundworker.jobs.JobEntityClassification;
import com.hedera.services.state.expiry.backgroundworker.jobs.JobStatus;
import com.hedera.services.state.expiry.renewal.RenewalRecordsHelper;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fcmap.FCMap;

import java.time.Instant;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;

/**
 * Represents the renewal process of a detached account with enough balance.
 */
public class DetachedAccountRenewal implements Job {
	private final MerkleAccount account;
	private final MerkleEntityId entityId;
	private final RenewalRecordsHelper recordsHelper;
	private final FeeCalculator fees;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;
	private final GlobalDynamicProperties dynamicProperties;
	private JobStatus status;
	private JobEntityClassification classification = JobEntityClassification.HEAVYWEIGHT;

	public DetachedAccountRenewal(
			final GlobalDynamicProperties globalDynamicProperties,
			final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			final MerkleAccount account,
			final MerkleEntityId entityId,
			final RenewalRecordsHelper recordsHelper,
			final FeeCalculator fees
	) {
		this.account = account;
		this.entityId = entityId;
		this.recordsHelper = recordsHelper;
		this.fees = fees;
		this.accounts = accounts;
		this.dynamicProperties = globalDynamicProperties;
	}

	public void renewLastClassifiedWith(long fee, long renewalPeriod) {
		assertHasLastClassifiedAccount();
		assertLastClassifiedAccountCanAfford(fee);

		final var currentAccounts = accounts.get();

		final var mutableLastClassified = currentAccounts.getForModify(entityId);
		final long newExpiry = mutableLastClassified.getExpiry() + renewalPeriod;
		final long newBalance = mutableLastClassified.getBalance() - fee;
		mutableLastClassified.setExpiry(newExpiry);
		mutableLastClassified.setBalanceUnchecked(newBalance);

		final var fundingId = fromAccountId(dynamicProperties.fundingAccount());
		final var mutableFundingAccount = currentAccounts.getForModify(fundingId);
		final long newFundingBalance = mutableFundingAccount.getBalance() + fee;
		mutableFundingAccount.setBalanceUnchecked(newFundingBalance);
	}

	@Override
	public JobStatus getStatus() {
		return status;
	}

	@Override
	public void setStatus(final JobStatus status) {
		this.status = status;
	}

	@Override
	public JobEntityClassification getClassification() {
		return classification;
	}

	@Override
	public boolean execute(final long now) {
		try {
			final var cycleTime = Instant.ofEpochSecond(now);
			final var lastClassified = account;
			final long reqPeriod = lastClassified.getAutoRenewSecs();
			final var usageAssessment = fees.assessCryptoAutoRenewal(lastClassified, reqPeriod, cycleTime);
			final long effPeriod = usageAssessment.renewalPeriod();
			final long renewalFee = usageAssessment.fee();
			renewLastClassifiedWith(renewalFee, effPeriod);
			recordsHelper.streamCryptoRenewal(entityId, renewalFee, lastClassified.getExpiry() + effPeriod);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public void reviewExistingEntities() {

	}

	private void assertHasLastClassifiedAccount() {
		if (account == null) {
			setStatus(JobStatus.FAILED);
			throw new IllegalStateException("Cannot remove a last classified account; none is present!");
		}
	}

	private void assertLastClassifiedAccountCanAfford(long fee) {
		if (account.getBalance() < fee) {
			setStatus(JobStatus.FAILED);
			var msg = "Cannot charge " + fee + " to " + entityId.toAbbrevString() + "!";
			throw new IllegalStateException(msg);
		}
	}

	@Override
	public long getAffectedId() {
		return entityId.getNum();
	}

}
