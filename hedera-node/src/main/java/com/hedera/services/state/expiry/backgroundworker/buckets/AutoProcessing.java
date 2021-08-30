package com.hedera.services.state.expiry.backgroundworker.buckets;

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

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.state.expiry.backgroundworker.jobs.Job;
import com.hedera.services.state.expiry.backgroundworker.jobs.JobStatus;
import com.hedera.services.state.expiry.backgroundworker.jobs.heavy.DetachedAccountRemoval;
import com.hedera.services.state.expiry.backgroundworker.jobs.heavy.DetachedAccountRenewal;
import com.hedera.services.state.expiry.renewal.ExpiredEntityClassification;
import com.hedera.services.state.expiry.renewal.RenewalRecordsHelper;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.store.tokens.TokenStore;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.fcmap.FCMap;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DELETED_TOKEN;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DETACHED_ACCOUNT;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.EXPIRED_ACCOUNT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.EXPIRED_TOKEN;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.EXPIRED_TOKEN_READY_TO_RENEW;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.OTHER;

/**
 * Responsible for handling heavyweight operations - entity renewal/expiration.
 * Each heavyweight operation is represented by a job. 
 */
public class AutoProcessing implements JobBucket {

	private final RenewalRecordsHelper recordsHelper;
	private final TokenStore tokenStore;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;
	private final Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenRels;
	/* Only needed for interoperability, will be removed during refactor */
	private final BackingStore<AccountID, MerkleAccount> backingAccounts;
	private final ServicesContext ctx;
	private final NetworkCtxManager networkCtxManager;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final FeeCalculator fees;
	private final long shard, realm, firstEntityToScan;

	private ConcurrentLinkedQueue<Job> jobs = new ConcurrentLinkedQueue<>();
	private MerkleAccount lastClassifiedAccount = null;
	private MerkleEntityId lastClassifiedEntityId;
	
	private long lastScanNum = 0;
	private int lastTouchedEntitiesCount = 0;
	private int lastNumIterations = 1;

	public AutoProcessing(
			final HederaNumbers hederaNumbers,
			final RenewalRecordsHelper recordsHelper,
			final TokenStore tokenStore,
			final GlobalDynamicProperties dynamicProperties,
			final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens,
			final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			final Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenRels,
			final BackingStore<AccountID, MerkleAccount> backingAccounts,
			final ServicesContext ctx,
			final NetworkCtxManager networkCtxManager,
			final Supplier<MerkleNetworkContext> networkCtx,
			final FeeCalculator fees
	) {
		this.recordsHelper = recordsHelper;
		this.tokenStore = tokenStore;
		this.dynamicProperties = dynamicProperties;
		this.tokens = tokens;
		this.accounts = accounts;
		this.tokenRels = tokenRels;
		this.backingAccounts = backingAccounts;
		this.ctx = ctx;
		this.networkCtxManager = networkCtxManager;
		this.networkCtx = networkCtx;
		this.fees = fees;

		this.shard = hederaNumbers.shard();
		this.realm = hederaNumbers.realm();
		this.firstEntityToScan = hederaNumbers.numReservedSystemEntities() + 1;
	}

	private ExpiredEntityClassification classify(long now, long entityNum) {
		lastClassifiedEntityId = new MerkleEntityId(shard, realm, entityNum);
		if (isIdOfAccount(lastClassifiedEntityId)) {
			return getExpiredAccountClassification(now);
		} else if (isIdOfToken(lastClassifiedEntityId)) {
			return getExpiredTokenClassification(now);
		}
		return OTHER;
	}

	private void prepare(long instantNow) {
		lastClassifiedAccount = null;
		lastClassifiedEntityId = null;
		if (!dynamicProperties.autoRenewEnabled()) {
			return;
		}

		final long wrapNum = ctx.seqNo().current();
		if (wrapNum == firstEntityToScan) {
			/* No non-system entities in the system, can abort */
			return;
		}

		final var curNetworkCtx = networkCtx.get();
		/* this property gives us a maximum of 2 new jobs per transaction */
		final int maxEntitiesToTouch = dynamicProperties.autoRenewMaxNumberOfEntitiesToRenewOrDelete();
		final int maxEntitiesToScan = dynamicProperties.autoRenewNumberOfEntitiesToScan();
		if (networkCtxManager.currentTxnIsFirstInConsensusSecond()) {
			curNetworkCtx.clearAutoRenewSummaryCounts();
		}
		lastNumIterations = 1;
		int entitiesToTouch = 0;
		long scanNum = ctx.lastScannedEntity();

		for (; lastNumIterations <= maxEntitiesToScan; lastNumIterations++) {
			scanNum++;
			if (scanNum >= wrapNum) {
				scanNum = firstEntityToScan;
			}
			Job job;
			final var entityClassification = classify(instantNow, scanNum);
			switch (entityClassification) {
				case DETACHED_ACCOUNT_GRACE_PERIOD_OVER:
					job = new DetachedAccountRemoval(lastClassifiedAccount, lastClassifiedEntityId, recordsHelper, tokens, tokenRels, backingAccounts);
					enqueueJob(job, scanNum);
					entitiesToTouch++;
					break;

				case EXPIRED_ACCOUNT_READY_TO_RENEW:
					job = new DetachedAccountRenewal(dynamicProperties, accounts, lastClassifiedAccount, lastClassifiedEntityId, recordsHelper, fees);
					enqueueJob(job, scanNum);
					entitiesToTouch++;
					break;
				default:
					break;
			}

			if (entitiesToTouch >= maxEntitiesToTouch) {
				/* Allow consistent calculation of num scanned below. */
				lastNumIterations++;
				break;
			}
		}

		lastScanNum = scanNum;
		lastTouchedEntitiesCount = entitiesToTouch;
	}
	
	@Override
	public void doPostTransactionJobs(long now) {
		prepare(now);
		for (final var job : jobs) {
			if (job.execute(now)) {
				jobs.remove(job);
			}
		}
		cleanupFinishedJobs();

		networkCtx.get().updateAutoRenewSummaryCounts(lastNumIterations - 1, lastTouchedEntitiesCount);
		ctx.updateLastScannedEntity(lastScanNum);
	}

	private void cleanupFinishedJobs() {
		jobs.removeIf(job -> job.getStatus().equals(JobStatus.DONE));
	}
	
	private boolean isIdOfAccount(MerkleEntityId id) {
		return accounts.get().containsKey(id);
	}

	private boolean isIdOfToken(MerkleEntityId id) {
		return tokens.get().containsKey(id);
	}

	private ExpiredEntityClassification getExpiredTokenClassification(final long now) {
		var currentTokens = tokens.get();
		var lastClassifiedToken = currentTokens.get(lastClassifiedEntityId);
		if (lastClassifiedToken.isDeleted()) {
			return DELETED_TOKEN;
		}
		if (lastClassifiedToken.expiry() > now) {


			if (lastClassifiedToken.hasAutoRenewAccount()) {
				var currAccounts = accounts.get();
				var autoRenewAccount = currAccounts.get(lastClassifiedToken.autoRenewAccount().asMerkle());
				if (autoRenewAccount.getBalance() > 0) {
					return EXPIRED_TOKEN_READY_TO_RENEW;
				} else {
					return EXPIRED_TOKEN;
				}
			}


		}
		return OTHER;
	}

	private ExpiredEntityClassification getExpiredAccountClassification(final long now) {
		var currentAccounts = accounts.get();
		lastClassifiedAccount = currentAccounts.get(lastClassifiedEntityId);
		if (lastClassifiedAccount.isSmartContract()) {
			return OTHER;
		}

		final long expiry = lastClassifiedAccount.getExpiry();
		if (expiry > now) {
			return OTHER;
		}

		if (lastClassifiedAccount.getBalance() > 0) {
			return EXPIRED_ACCOUNT_READY_TO_RENEW;
		}
		if (lastClassifiedAccount.isDeleted()) {
			return DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
		}

		final long gracePeriodEnd = expiry + dynamicProperties.autoRenewGracePeriod();
		if (gracePeriodEnd > now) {
			return DETACHED_ACCOUNT;
		}
		final var grpcId = lastClassifiedEntityId.toAccountId();
		if (tokenStore.isKnownTreasury(grpcId)) {
			return DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
		}

		return DETACHED_ACCOUNT_GRACE_PERIOD_OVER;

	}

	private void enqueueJob(Job job, long entityNum) {
		/* we should not have 2 jobs for the same entity */
		if (!hasJobForEntity(entityNum)) {
			jobs.add(job);
		}
	}
	
	private boolean hasJobForEntity(long entityNum) {
		return this.jobs.stream().anyMatch(j -> (j.getAffectedId() == entityNum));
	}
}
