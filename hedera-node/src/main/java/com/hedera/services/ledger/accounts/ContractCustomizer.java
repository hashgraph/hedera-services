package com.hedera.services.ledger.accounts;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Optional;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;

/**
 * Encapsulates a set of customizations to a smart contract. Primarily delegates to an {@link HederaAccountCustomizer},
 * but with a bit of extra logic to deal with {@link com.hedera.services.legacy.core.jproto.JContractIDKey} management.
 */
public class ContractCustomizer {
	// Null if the contract is immutable; then its key derives from its entity id
	private final JKey cryptoAdminKey;
	private final HederaAccountCustomizer accountCustomizer;

	public ContractCustomizer(final HederaAccountCustomizer accountCustomizer) {
		this(null, accountCustomizer);
	}

	public ContractCustomizer(final @Nullable JKey cryptoAdminKey, final HederaAccountCustomizer accountCustomizer) {
		this.cryptoAdminKey = cryptoAdminKey;
		this.accountCustomizer = accountCustomizer;
	}

	/**
	 * Given a {@link ContractCreateTransactionBody}, a decoded admin key, and the current consensus time,
	 * returns a customizer appropriate for the contract created from this HAPI operation.
	 *
	 * @param decodedKey
	 * 		the key implied by the HAPI operation
	 * @param consensusTime
	 * 		the consensus time of the ContractCreate
	 * @param op
	 * 		the details of the HAPI operation
	 * @return an appropriate top-level customizer
	 */
	public static ContractCustomizer fromHapiCreation(
			final JKey decodedKey,
			final Instant consensusTime,
			final ContractCreateTransactionBody op
	) {
		final var autoRenewPeriod = op.getAutoRenewPeriod().getSeconds();
		final var expiry = consensusTime.getEpochSecond() + autoRenewPeriod;
		final var stakedId = getStakedId(op.getStakedAccountId(), op.getStakedNodeId());

		final var key = (decodedKey instanceof JContractIDKey) ? null : decodedKey;
		final var customizer = new HederaAccountCustomizer()
				.memo(op.getMemo())
				.expiry(expiry)
				.autoRenewPeriod(op.getAutoRenewPeriod().getSeconds())
				.isDeclinedReward(op.getDeclineReward())
				.isSmartContract(true);
		if (stakedId.isPresent()) {
			customizer.stakedId(stakedId.get());
		}
		return new ContractCustomizer(key, customizer);
	}

	/**
	 * Given a {@link TransactionalLedger} containing the sponsor contract, returns a customizer appropriate
	 * to use for contracts created by the sponsor via internal {@code CONTRACT_CREATION} message calls.
	 *
	 * @param sponsor
	 * 		the sending contract
	 * @param ledger
	 * 		the containing ledger
	 * @return an appropriate child customizer
	 */
	public static ContractCustomizer fromSponsorContract(
			final AccountID sponsor,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger
	) {
		var key = (JKey) ledger.get(sponsor, KEY);
		if (key instanceof JContractIDKey) {
			key = null;
		}
		final var customizer = new HederaAccountCustomizer()
				.memo((String) ledger.get(sponsor, MEMO))
				.expiry((long) ledger.get(sponsor, EXPIRY))
				.autoRenewPeriod((long) ledger.get(sponsor, AUTO_RENEW_PERIOD))
				.isSmartContract(true);
		return new ContractCustomizer(key, customizer);
	}

	/**
	 * Given a target contract account id and the containing ledger, makes various calls to
	 * {@link TransactionalLedger#set(Object, Enum, Object)} to initialize the contract's properties.
	 *
	 * @param id
	 * 		the id of the target contract
	 * @param ledger
	 * 		its containing ledger
	 */
	public void customize(
			final AccountID id,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger
	) {
		accountCustomizer.customize(id, ledger);
		final var newKey = (cryptoAdminKey == null)
				? new JContractIDKey(id.getShardNum(), id.getRealmNum(), id.getAccountNum())
				: cryptoAdminKey;
		ledger.set(id, KEY, newKey);
	}

	/**
	 * Updates a synthetic {@link ContractCreateTransactionBody} to represent the creation
	 * of a contract with this customizer's properties.
	 *
	 * @param op
	 * 		the synthetic creation to customize
	 */
	public void customizeSynthetic(final ContractCreateTransactionBody.Builder op) {
		if (cryptoAdminKey != null) {
			op.setAdminKey(asKeyUnchecked(cryptoAdminKey));
		}
		accountCustomizer.customizeSynthetic(op);
	}

	/**
	 * Gets the stakedId from the provided staked_account_id or staked_node_id.
	 *
	 * @param stakedAccountId
	 * 		given staked_account_id
	 * @param stakedNodeId
	 * 		given staked_node_id
	 * @return valid staked id
	 */
	public static Optional<Long> getStakedId(final AccountID stakedAccountId, final long stakedNodeId) {
		if (stakedAccountId != null && !stakedAccountId.equals(AccountID.getDefaultInstance())) {
			return Optional.of(stakedAccountId.getAccountNum());
		} else if (stakedNodeId > 0) {
			return Optional.of((-1 * stakedNodeId));
		}
		return Optional.empty();
	}
}
