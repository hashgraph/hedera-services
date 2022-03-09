package com.hedera.services.contracts.sources;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.ActivationTest;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.merkle.map.MerkleMap;
import org.hyperledger.besu.datatypes.Address;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;

@Singleton
public class TxnAwareSoliditySigsVerifier implements SoliditySigsVerifier {
	private final ActivationTest activationTest;
	private final TransactionContext txnCtx;
	private final BiPredicate<JKey, TransactionSignature> cryptoValidity;
	private final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private WorldLedgers ledgers;

	@Inject
	public TxnAwareSoliditySigsVerifier(
			final ActivationTest activationTest,
			final TransactionContext txnCtx,
			final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens,
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final BiPredicate<JKey, TransactionSignature> cryptoValidity
	) {
		this.txnCtx = txnCtx;
		this.tokens = tokens;
		this.accounts = accounts;
		this.activationTest = activationTest;
		this.cryptoValidity = cryptoValidity;
	}

	public void setWorldLedgers(final WorldLedgers ledgers) {
		this.ledgers = ledgers;
	}

	@Override
	public boolean hasActiveKey(
			final Address accountAddress,
			final Address recipient,
			final Address contract,
			final Address activeContract,
			final ContractAliases aliases
	) {
		final var accountId = EntityIdUtils.accountIdFromEvmAddress(accountAddress);
		final var account = ledgers != null ?
				ledgers.accounts().getImmutableRef(accountId) : null;
		validateTrue(account != null, INVALID_ACCOUNT_ID);

		if (accountAddress.equals(activeContract)) {
			return true;
		}

		return isActiveInFrame(account.getAccountKey(), recipient, contract, activeContract, aliases);
	}

	@Override
	public boolean hasActiveSupplyKey(
			final Address tokenAddress,
			final Address recipient,
			final Address contract,
			final Address activeContract,
			final ContractAliases aliases
	) {
		final var tokenId = EntityIdUtils.tokenIdFromEvmAddress(tokenAddress);
		final var simpleId = Id.fromGrpcToken(tokenId);
		final var entityNum = simpleId.asEntityNum();
		final var token = tokens.get().get(entityNum);
		validateTrue(token != null, INVALID_TOKEN_ID);
		validateTrue(token.hasSupplyKey(), TOKEN_HAS_NO_SUPPLY_KEY);
		return isActiveInFrame(token.getSupplyKey(), recipient, contract, activeContract, aliases);
	}

	@Override
	public boolean hasActiveKeyOrNoReceiverSigReq(
			final Address target,
			final Address recipient,
			final Address contract,
			final Address activeContract,
			final ContractAliases aliases
	) {
		final var accountId = EntityIdUtils.accountIdFromEvmAddress(target);
		if (txnCtx.activePayer().equals(accountId)) {
			return true;
		}
		final var requiredKey = receiverSigKeyIfAnyOf(accountId);
		return requiredKey.map(key ->
				isActiveInFrame(key, recipient, contract, activeContract, aliases)).orElse(true);
	}

	private boolean isActiveInFrame(
			final JKey key,
			final Address recipient,
			final Address contract,
			final Address activeContract,
			final ContractAliases aliases
	) {
		final var pkToCryptoSigsFn = txnCtx.accessor().getRationalizedPkToCryptoSigFn();
		return activationTest.test(
				key,
				pkToCryptoSigsFn,
				validityTestFor(recipient, contract, activeContract, aliases));
	}

	BiPredicate<JKey, TransactionSignature> validityTestFor(
			final Address recipient,
			final Address contract,
			final Address activeContract,
			final ContractAliases aliases
	) {
		final var isDelegateCall = !contract.equals(recipient);

		/* Note that when this observer is used directly above in isActiveInFrame(), it will be
		 * called  with each primitive key in the top-level Hedera key of interest, along with
		 * that key's verified cryptographic signature (if any was available in the sigMap). */
		return (key, sig) -> {
			if (key.hasDelegatableContractId() || key.hasDelegatableContractAlias()) {
				final var controllingId = key.hasDelegatableContractId()
						? key.getDelegatableContractIdKey().getContractID()
						: key.getDelegatableContractAliasKey().getContractID();
				final var controllingContract =
						aliases.currentAddress(controllingId);
				return controllingContract.equals(activeContract);
			} else if (key.hasContractID() || key.hasContractAlias()) {
				final var controllingId = key.hasContractID()
						? key.getContractIDKey().getContractID()
						: key.getContractAliasKey().getContractID();
				final var controllingContract = aliases.currentAddress(controllingId);
				return !isDelegateCall && controllingContract.equals(activeContract);
			} else {
				/* Otherwise apply the standard cryptographic validity test */
				return cryptoValidity.test(key, sig);
			}
		};
	}

	private Optional<JKey> receiverSigKeyIfAnyOf(final AccountID id) {
		return Optional.ofNullable(accounts.get().get(fromAccountId(id)))
				.filter(account -> !account.isSmartContract())
				.filter(MerkleAccount::isReceiverSigRequired)
				.map(MerkleAccount::getAccountKey);
	}
}
