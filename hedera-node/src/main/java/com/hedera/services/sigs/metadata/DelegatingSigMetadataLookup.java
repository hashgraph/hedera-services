package com.hedera.services.sigs.metadata;

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

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.sigs.metadata.lookups.AccountSigMetaLookup;
import com.hedera.services.sigs.metadata.lookups.BackedAccountLookup;
import com.hedera.services.sigs.metadata.lookups.ContractSigMetaLookup;
import com.hedera.services.sigs.metadata.lookups.DefaultAccountLookup;
import com.hedera.services.sigs.metadata.lookups.DefaultContractLookup;
import com.hedera.services.sigs.metadata.lookups.DefaultTopicLookup;
import com.hedera.services.sigs.metadata.lookups.FileSigMetaLookup;
import com.hedera.services.sigs.metadata.lookups.HfsSigMetaLookup;
import com.hedera.services.sigs.metadata.lookups.RetryingAccountLookup;
import com.hedera.services.sigs.metadata.lookups.SafeLookupResult;
import com.hedera.services.sigs.metadata.lookups.TopicSigMetaLookup;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.merkle.map.MerkleMap;

import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.utils.SleepingPause.SLEEPING_PAUSE;

/**
 * Convenience class that gives unified access to Hedera signing metadata by
 * delegating to type-specific lookups.
 */
public final class DelegatingSigMetadataLookup implements SigMetadataLookup {
	private final FileSigMetaLookup fileSigMetaLookup;
	private final AccountSigMetaLookup accountSigMetaLookup;
	private final ContractSigMetaLookup contractSigMetaLookup;
	private final TopicSigMetaLookup topicSigMetaLookup;

	private final Function<TokenID, SafeLookupResult<TokenSigningMetadata>> tokenSigMetaLookup;
	private final Function<ScheduleID, SafeLookupResult<ScheduleSigningMetadata>> scheduleSigMetaLookup;

	public static DelegatingSigMetadataLookup backedLookupsFor(
			final HfsSigMetaLookup hfsSigMetaLookup,
			final BackingStore<AccountID, MerkleAccount> backingAccounts,
			final Supplier<MerkleMap<EntityNum, MerkleTopic>> topics,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Function<TokenID, SafeLookupResult<TokenSigningMetadata>> tokenLookup,
			final Function<ScheduleID, SafeLookupResult<ScheduleSigningMetadata>> scheduleSigMetaLookup
	) {
		return new DelegatingSigMetadataLookup(
				hfsSigMetaLookup,
				new BackedAccountLookup(backingAccounts),
				new DefaultContractLookup(accounts),
				new DefaultTopicLookup(topics),
				tokenLookup,
				scheduleSigMetaLookup);
	}

	public static DelegatingSigMetadataLookup defaultLookupsFor(
			final AliasManager aliasManager,
			final HfsSigMetaLookup hfsSigMetaLookup,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<MerkleMap<EntityNum, MerkleTopic>> topics,
			final Function<TokenID, SafeLookupResult<TokenSigningMetadata>> tokenLookup,
			final Function<ScheduleID, SafeLookupResult<ScheduleSigningMetadata>> scheduleLookup
	) {
		return new DelegatingSigMetadataLookup(
				hfsSigMetaLookup,
				new DefaultAccountLookup(aliasManager, accounts),
				new DefaultContractLookup(accounts),
				new DefaultTopicLookup(topics),
				tokenLookup,
				scheduleLookup);
	}

	public static DelegatingSigMetadataLookup defaultLookupsPlusAccountRetriesFor(
			final HfsSigMetaLookup hfsSigMetaLookup,
			final AliasManager aliasManager,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<MerkleMap<EntityNum, MerkleTopic>> topics,
			final Function<TokenID, SafeLookupResult<TokenSigningMetadata>> tokenLookup,
			final Function<ScheduleID, SafeLookupResult<ScheduleSigningMetadata>> scheduleLookup,
			final int maxRetries,
			final int retryWaitIncrementMs,
			final MiscRunningAvgs runningAvgs,
			final MiscSpeedometers speedometers
	) {
		final var accountLookup = new RetryingAccountLookup(
				accounts,
				maxRetries,
				retryWaitIncrementMs,
				SLEEPING_PAUSE,
				aliasManager,
				runningAvgs,
				speedometers);
		return new DelegatingSigMetadataLookup(
				hfsSigMetaLookup,
				accountLookup,
				new DefaultContractLookup(accounts),
				new DefaultTopicLookup(topics),
				tokenLookup,
				scheduleLookup);
	}

	public static DelegatingSigMetadataLookup defaultAccountRetryingLookupsFor(
			final AliasManager aliasManager,
			final HfsSigMetaLookup hfsSigMetaLookup,
			final NodeLocalProperties properties,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<MerkleMap<EntityNum, MerkleTopic>> topics,
			final Function<TokenID, SafeLookupResult<TokenSigningMetadata>> tokenLookup,
			final Function<ScheduleID, SafeLookupResult<ScheduleSigningMetadata>> scheduleLookup,
			final MiscRunningAvgs runningAvgs,
			final MiscSpeedometers speedometers
	) {
		final var accountLookup =
				new RetryingAccountLookup(SLEEPING_PAUSE, aliasManager, properties, accounts, runningAvgs, speedometers);
		return new DelegatingSigMetadataLookup(
				hfsSigMetaLookup,
				accountLookup,
				new DefaultContractLookup(accounts),
				new DefaultTopicLookup(topics),
				tokenLookup,
				scheduleLookup);
	}

	public DelegatingSigMetadataLookup(
			final FileSigMetaLookup fileSigMetaLookup,
			final AccountSigMetaLookup accountSigMetaLookup,
			final ContractSigMetaLookup contractSigMetaLookup,
			final TopicSigMetaLookup topicSigMetaLookup,
			final Function<TokenID, SafeLookupResult<TokenSigningMetadata>> tokenSigMetaLookup,
			final Function<ScheduleID, SafeLookupResult<ScheduleSigningMetadata>> scheduleSigMetaLookup
	) {
		this.fileSigMetaLookup = fileSigMetaLookup;
		this.accountSigMetaLookup = accountSigMetaLookup;
		this.contractSigMetaLookup = contractSigMetaLookup;
		this.topicSigMetaLookup = topicSigMetaLookup;
		this.tokenSigMetaLookup = tokenSigMetaLookup;
		this.scheduleSigMetaLookup = scheduleSigMetaLookup;
	}

	@Override
	public SafeLookupResult<ContractSigningMetadata> contractSigningMetaFor(final ContractID id) {
		return contractSigMetaLookup.safeLookup(id);
	}

	@Override
	public SafeLookupResult<FileSigningMetadata> fileSigningMetaFor(final FileID id) {
		return fileSigMetaLookup.safeLookup(id);
	}

	@Override
	public SafeLookupResult<ScheduleSigningMetadata> scheduleSigningMetaFor(final ScheduleID id) {
		return scheduleSigMetaLookup.apply(id);
	}

	@Override
	public SafeLookupResult<AccountSigningMetadata> accountSigningMetaFor(final AccountID id) {
		return accountSigMetaLookup.safeLookup(id);
	}

	@Override
	public SafeLookupResult<TopicSigningMetadata> topicSigningMetaFor(final TopicID id) {
		return topicSigMetaLookup.safeLookup(id);
	}

	@Override
	public SafeLookupResult<TokenSigningMetadata> tokenSigningMetaFor(final TokenID id) {
		return tokenSigMetaLookup.apply(id);
	}

	@Override
	public SafeLookupResult<AccountSigningMetadata> aliasableAccountSigningMetaFor(AccountID idOrAlias) {
		return accountSigMetaLookup.aliasableSafeLookup(idOrAlias);
	}
}
