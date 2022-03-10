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
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.crypto.TransactionSignature;
import org.hyperledger.besu.datatypes.Address;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.function.BiPredicate;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;

@Singleton
public class TxnAwareEvmSigsVerifier implements EvmSigsVerifier {
	private final ActivationTest activationTest;
	private final TransactionContext txnCtx;
	private final BiPredicate<JKey, TransactionSignature> cryptoValidity;

	@Inject
	public TxnAwareEvmSigsVerifier(
			final ActivationTest activationTest,
			final TransactionContext txnCtx,
			final BiPredicate<JKey, TransactionSignature> cryptoValidity
	) {
		this.txnCtx = txnCtx;
		this.activationTest = activationTest;
		this.cryptoValidity = cryptoValidity;
	}

	@Override
	public boolean hasActiveKey(
			final Address accountAddress,
			final Address recipient,
			final Address contract,
			final Address activeContract,
			final WorldLedgers worldLedgers
	) {
		final var accountId = EntityIdUtils.accountIdFromEvmAddress(accountAddress);
		final var account = worldLedgers != null ?
				Optional.ofNullable(worldLedgers.accounts().getImmutableRef(accountId)) : Optional.empty();

		validateTrue(account.isPresent(), INVALID_ACCOUNT_ID);

		if (accountAddress.equals(activeContract)) {
			return true;
		}

		final MerkleAccount merkleAccount = account.map(MerkleAccount.class::cast).orElseGet(MerkleAccount::new);
		return merkleAccount.getAccountKey() != null && isActiveInFrame(merkleAccount.getAccountKey(), recipient, contract,
				activeContract,
				worldLedgers.aliases());
	}

	@Override
	public boolean hasActiveSupplyKey(
			final Address tokenAddress,
			final Address recipient,
			final Address contract,
			final Address activeContract,
			final WorldLedgers worldLedgers
	) {
		final var tokenId = EntityIdUtils.tokenIdFromEvmAddress(tokenAddress);
		final var token = worldLedgers != null ?
				Optional.ofNullable(worldLedgers.tokens().getImmutableRef(tokenId)) : Optional.empty();

		validateTrue(token.isPresent(), INVALID_TOKEN_ID);

		final MerkleToken merkleToken = token.map(MerkleToken.class::cast).orElseGet(MerkleToken::new);
		validateTrue(merkleToken.hasSupplyKey(), TOKEN_HAS_NO_SUPPLY_KEY);
		return isActiveInFrame(merkleToken.getSupplyKey(), recipient, contract, activeContract, worldLedgers.aliases());
	}

	@Override
	public boolean hasActiveKeyOrNoReceiverSigReq(
			final Address target,
			final Address recipient,
			final Address contract,
			final Address activeContract,
			final WorldLedgers worldLedgers
	) {
		final var accountId = EntityIdUtils.accountIdFromEvmAddress(target);
		if (txnCtx.activePayer().equals(accountId)) {
			return true;
		}
		final var requiredKey = receiverSigKeyIfAnyOf(accountId, worldLedgers);
		return requiredKey.map(key ->
				isActiveInFrame(key, recipient, contract, activeContract, worldLedgers.aliases())).orElse(true);
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

	private Optional<JKey> receiverSigKeyIfAnyOf(final AccountID id, final WorldLedgers worldLedgers) {
		final var merkleAccount = worldLedgers != null ? Optional.ofNullable(worldLedgers.accounts().getImmutableRef(id)) :
				Optional.empty();

		return merkleAccount
				.filter(account -> !((MerkleAccount)account).isSmartContract())
				.filter(account -> ((MerkleAccount)account).isReceiverSigRequired())
				.map(account -> ((MerkleAccount)account).getAccountKey());
	}
}