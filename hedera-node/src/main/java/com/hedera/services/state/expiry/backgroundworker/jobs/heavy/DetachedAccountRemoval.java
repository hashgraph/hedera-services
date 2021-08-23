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

import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.state.expiry.backgroundworker.jobs.Job;
import com.hedera.services.state.expiry.backgroundworker.jobs.JobEntityClassification;
import com.hedera.services.state.expiry.backgroundworker.jobs.JobStatus;
import com.hedera.services.state.expiry.renewal.RenewalRecordsHelper;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.ledger.HederaLedger.ACCOUNT_ID_COMPARATOR;
import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;

/**
 * Represents the removal process of a detached account.
 */
public class DetachedAccountRemoval implements Job {

	private final Logger log = LogManager.getLogger(DetachedAccountRemoval.class);

	private final MerkleAccount account;
	private final MerkleEntityId entityId;
	private final RenewalRecordsHelper recordsHelper;
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;
	private final Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenRels;
	private final BackingStore<AccountID, MerkleAccount> backingAccounts;

	private JobStatus status;
	private JobEntityClassification classification = JobEntityClassification.HEAVYWEIGHT;

	public DetachedAccountRemoval(
			final MerkleAccount account,
			final MerkleEntityId entityId,
			final RenewalRecordsHelper recordsHelper,
			final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens,
			final Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenRels,
			final BackingStore<AccountID, MerkleAccount> backingAccounts
	) {
		this.account = account;
		this.entityId = entityId;
		this.recordsHelper = recordsHelper;
		this.tokens = tokens;
		this.tokenRels = tokenRels;
		this.backingAccounts = backingAccounts;
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
			var tokensDisplaced = removeLastClassifiedAccount();
			recordsHelper.streamCryptoRemoval(entityId, tokensDisplaced.getLeft(), tokensDisplaced.getRight());
			return true;
		}catch (Exception ignore) {
			setStatus(JobStatus.FAILED);
			return false;
		}
	}

	public Pair<List<EntityId>, List<CurrencyAdjustments>> removeLastClassifiedAccount() {
		assertHasLastClassifiedAccount();
		if (account.getBalance() > 0) {
			throw new IllegalStateException("Cannot remove the last classified account, has non-zero balance!");
		}

		Pair<List<EntityId>, List<CurrencyAdjustments>> displacements = Pair.of(new ArrayList<>(), new ArrayList<>());
		final var lastClassifiedTokens = account.tokens();
		if (lastClassifiedTokens.numAssociations() > 0) {
			final var grpcId = entityId.toAccountId();
			final var currentTokens = tokens.get();
			for (var tId : lastClassifiedTokens.asTokenIds()) {
				doReturnToTreasury(grpcId, tId, displacements, currentTokens);
			}
		}

		/* When refactoring to remove this backingAccounts, please remove the account from accounts instead.*/
		backingAccounts.remove(entityId.toAccountId());

		log.debug("Removed {}, displacing {}", entityId, displacements);

		return displacements;
	}

	private void doReturnToTreasury(
			AccountID expired,
			TokenID scopedToken,
			Pair<List<EntityId>, List<CurrencyAdjustments>> displacements,
			FCMap<MerkleEntityId, MerkleToken> currentTokens
	) {
		final var currentTokenRels = tokenRels.get();
		final var expiredRel = fromAccountTokenRel(expired, scopedToken);
		final var relStatus = currentTokenRels.get(expiredRel);
		final long balance = relStatus.getBalance();

		currentTokenRels.remove(expiredRel);

		final var tKey = MerkleEntityId.fromTokenId(scopedToken);
		if (!currentTokens.containsKey(tKey)) {
			return;
		}

		final var token = currentTokens.get(tKey);
		if (token.isDeleted()) {
			return;
		}

		if (balance == 0L) {
			return;
		}

		final var treasury = token.treasury().toGrpcAccountId();
		final boolean expiredFirst = ACCOUNT_ID_COMPARATOR.compare(expired, treasury) < 0;
		displacements.getLeft().add(EntityId.fromGrpcTokenId(scopedToken));
		final var expiredId = EntityId.fromGrpcAccountId(expired);
		final var treasuryId = EntityId.fromGrpcAccountId(treasury);
		displacements.getRight().add(new CurrencyAdjustments(
				expiredFirst ? new long[]{-balance, +balance} : new long[]{+balance, -balance},
				expiredFirst ? List.of(expiredId, treasuryId) : List.of(treasuryId, expiredId)
		));

		final var treasuryRel = fromAccountTokenRel(treasury, scopedToken);
		final var mutableTreasuryRelStatus = currentTokenRels.getForModify(treasuryRel);
		final long newTreasuryBalance = mutableTreasuryRelStatus.getBalance() + balance;
		mutableTreasuryRelStatus.setBalance(newTreasuryBalance);
	}

	private void assertHasLastClassifiedAccount() {
		if (account == null) {
			throw new IllegalStateException("Cannot remove a last classified account; none is present!");
		}
	}

	@Override
	public void reviewExistingEntities() {
		// NO-OP for this job
	}

	@Override
	public EntityId getAffectedEntityId() {
		return new EntityId(entityId.getShard(), entityId.getRealm(), entityId.getNum());
	}

}
