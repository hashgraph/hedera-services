package com.hedera.services.store.contracts;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.AccountsCommitInterceptor;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.TokenRelsCommitInterceptor;
import com.hedera.services.ledger.TokensCommitInterceptor;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.UniqueTokensCommitInterceptor;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.accounts.StackedContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.util.Objects;

import static com.hedera.services.ledger.TransactionalLedger.activeLedgerWrapping;
import static com.hedera.services.ledger.properties.AccountProperty.ALIAS;
import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.ledger.properties.TokenProperty.TREASURY;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;

public class WorldLedgers {
	private final ContractAliases aliases;
	private final StaticEntityAccess staticEntityAccess;
	private final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger;
	private final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
	private final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	private final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;

	public static WorldLedgers staticLedgersWith(
			final ContractAliases aliases,
			final StaticEntityAccess staticEntityAccess
	) {
		return new WorldLedgers(aliases, staticEntityAccess);
	}

	public WorldLedgers(
			final ContractAliases aliases,
			final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger,
			final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger,
			final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger
	) {
		this.tokenRelsLedger = tokenRelsLedger;
		this.accountsLedger = accountsLedger;
		this.tokensLedger = tokensLedger;
		this.nftsLedger = nftsLedger;
		this.aliases = aliases;

		staticEntityAccess = null;
	}

	private WorldLedgers(final ContractAliases aliases, final StaticEntityAccess staticEntityAccess) {
		tokenRelsLedger = null;
		accountsLedger = null;
		tokensLedger = null;
		nftsLedger = null;

		this.aliases = aliases;
		this.staticEntityAccess = staticEntityAccess;
	}

	public Address ownerOf(final NftId nft) {
		if (!areMutable()) {
			throw new IllegalStateException("Cannot look up owner from unavailable ledgers");
		}
		var owner = (EntityId) nftsLedger.get(nft, OWNER);
		if (MISSING_ENTITY_ID.equals(owner)) {
			owner = (EntityId) tokensLedger.get(nft.tokenId(), TREASURY);
		}
		return owner.toEvmAddress();
	}

	public Address canonicalAddress(final Address addressOrAlias) {
		if (aliases.isInUse(addressOrAlias)) {
			return addressOrAlias;
		}

		return getAddressOrAlias(addressOrAlias);
	}

	public Address getAddressOrAlias(final Address address) {
		final var sourceId = accountIdFromEvmAddress(address);
		final ByteString alias;
		if (accountsLedger != null) {
			if (!accountsLedger.exists(sourceId)) {
				return address;
			}
			alias = (ByteString) accountsLedger.get(sourceId, ALIAS);
		} else {
			Objects.requireNonNull(staticEntityAccess, "Null ledgers must imply non-null static access");
			if (!staticEntityAccess.isExtant(sourceId)) {
				return address;
			}
			alias = staticEntityAccess.alias(sourceId);
		}
		if (!alias.isEmpty()) {
			return Address.wrap(Bytes.wrap(alias.toByteArray()));
		} else {
			return address;
		}
	}

	public boolean hasAlias(final Address address) {
		final var sourceId = accountIdFromEvmAddress(address);
		final ByteString alias;
		if (accountsLedger != null) {
			if (!accountsLedger.exists(sourceId)) {
				return false;
			}
			alias = (ByteString) accountsLedger.get(sourceId, ALIAS);
		} else {
			Objects.requireNonNull(staticEntityAccess, "Null ledgers must imply non-null static access");
			if (!staticEntityAccess.isExtant(sourceId)) {
				return false;
			}
			alias = staticEntityAccess.alias(sourceId);
		}
		return !alias.isEmpty();
	}

	public void commit() {
		if (areMutable()) {
			aliases.commit(null);
			commitLedgers();
		}
	}

	public void commit(final SigImpactHistorian sigImpactHistorian) {
		if (areMutable()) {
			aliases.commit(sigImpactHistorian);
			commitLedgers();
		}
	}

	private void commitLedgers() {
		tokenRelsLedger.commit();
		accountsLedger.commit();
		nftsLedger.commit();
		tokensLedger.commit();
	}

	public void revert() {
		if (areMutable()) {
			tokenRelsLedger.rollback();
			accountsLedger.rollback();
			nftsLedger.rollback();
			tokensLedger.rollback();
			aliases.revert();

			/* Since AbstractMessageProcessor.clearAccumulatedStateBesidesGasAndOutput() will make a
			 * second token call to commit() after the initial revert(), we want to keep these ledgers
			 * in an active transaction. */
			tokenRelsLedger.begin();
			accountsLedger.begin();
			nftsLedger.begin();
			tokensLedger.begin();
		}
	}

	public boolean areMutable() {
		return nftsLedger != null &&
				tokensLedger != null &&
				accountsLedger != null &&
				tokenRelsLedger != null;
	}

	public WorldLedgers wrapped() {
		if (!areMutable()) {
			return staticLedgersWith(StackedContractAliases.wrapping(aliases), staticEntityAccess);
		}

		return new WorldLedgers(
				StackedContractAliases.wrapping(aliases),
				activeLedgerWrapping(tokenRelsLedger),
				activeLedgerWrapping(accountsLedger),
				activeLedgerWrapping(nftsLedger),
				activeLedgerWrapping(tokensLedger));
	}

	public WorldLedgers wrapped(final SideEffectsTracker sideEffectsTracker) {
		if (!areMutable()) {
			return staticLedgersWith(StackedContractAliases.wrapping(aliases), staticEntityAccess);
		}

		final var tokensCommitInterceptor = new TokensCommitInterceptor(sideEffectsTracker);
		final var tokenRelsCommitInterceptor = new TokenRelsCommitInterceptor(sideEffectsTracker);
		final var uniqueTokensCommitInterceptor = new UniqueTokensCommitInterceptor(sideEffectsTracker);
		final var accountsCommitInterceptor = new AccountsCommitInterceptor(sideEffectsTracker);

		final var wrappedTokenRelsLedger = activeLedgerWrapping(tokenRelsLedger);
		wrappedTokenRelsLedger.setCommitInterceptor(tokenRelsCommitInterceptor);
		final var wrappedAccountsLedger = activeLedgerWrapping(accountsLedger);
		wrappedAccountsLedger.setCommitInterceptor(accountsCommitInterceptor);
		final var wrappedNftsLedger = activeLedgerWrapping(nftsLedger);
		wrappedNftsLedger.setCommitInterceptor(uniqueTokensCommitInterceptor);
		final var wrappedTokensLedger = activeLedgerWrapping(tokensLedger);
		wrappedTokensLedger.setCommitInterceptor(tokensCommitInterceptor);

		return new WorldLedgers(
				StackedContractAliases.wrapping(aliases),
				wrappedTokenRelsLedger,
				wrappedAccountsLedger,
				wrappedNftsLedger,
				wrappedTokensLedger);
	}

	public ContractAliases aliases() {
		return aliases;
	}

	public TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRels() {
		return tokenRelsLedger;
	}

	public TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts() {
		return accountsLedger;
	}

	public TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nfts() {
		return nftsLedger;
	}

	public TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens() {
		return tokensLedger;
	}
}
