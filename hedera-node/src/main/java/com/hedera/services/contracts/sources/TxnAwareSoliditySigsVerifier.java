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
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
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

import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.contractParsedFromSolidityAddress;
import static com.hedera.services.utils.EntityNum.fromAccountId;

@Singleton
public class TxnAwareSoliditySigsVerifier implements SoliditySigsVerifier {
	private final ActivationTest activationTest;
	private final TransactionContext txnCtx;
	private final BiPredicate<JKey, TransactionSignature> cryptoValidity;
	private final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;

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

	@Override
	public boolean hasActiveKey(final Address accountAddress, final Address recipient, final Address contract,
								final Address sender) {
		final var accountId = EntityIdUtils.accountParsedFromSolidityAddress(accountAddress);
		final var simpleId = Id.fromGrpcAccount(accountId);
		final var entityNum = simpleId.asEntityNum();
		final var account = accounts.get().get(entityNum);
		if (account == null) {
			throw new IllegalArgumentException("Cannot test key activation for missing account " + accountId);
		}
		return isActiveInFrame(account.getAccountKey(), recipient, contract, sender);
	}

	@Override
	public boolean hasActiveSupplyKey(Address tokenAddress, Address recipient, Address contract, final Address contractAddressExpectedToBeSigned) {
		final var tokenId = EntityIdUtils.tokenParsedFromSolidityAddress(tokenAddress);
		final var simpleId = Id.fromGrpcToken(tokenId);
		final var entityNum = simpleId.asEntityNum();
		final var token = tokens.get().get(entityNum);
		if (token == null) {
			throw new IllegalArgumentException(
					"Cannot test supply key activation for missing token " + tokenId);
		}
		if (!token.hasSupplyKey()) {
			throw new IllegalArgumentException(
					"Cannot test supply key activation, as token " + tokenId + " does not have one");
		}
		return isActiveInFrame(token.getSupplyKey(), recipient, contract, contractAddressExpectedToBeSigned);
	}

	@Override
	public boolean hasActiveKeyOrNoReceiverSigReq(
			final Address target,
			final Address recipient,
			final Address contract,
			final Address contractAddressExpectedToBeSigned
	) {
		final var accountId = accountParsedFromSolidityAddress(target);
		if (txnCtx.activePayer().equals(accountId)) {
			return true;
		}
		final var requiredKey = receiverSigKeyIfAnyOf(accountId);
		if (requiredKey.isPresent()) {
			return isActiveInFrame(requiredKey.get(), recipient, contract, contractAddressExpectedToBeSigned);
		} else {
			return true;
		}
	}

	BiPredicate<JKey, TransactionSignature> validityTestFor(final Address recipient, final Address contract,
															final Address contractAddressExpectedToBeSigned) {
		final var contractIdExpectedToBeSigned =
				contractParsedFromSolidityAddress(contractAddressExpectedToBeSigned);
		final var isDelegateCall = !contract.equals(recipient);

		/* Note that when this observer is used directly above in isActive(), it will be called
		 * with each primitive key in the top-level Hedera key of interest, along with that key's
		 * verified cryptographic signature (if any was available in the sigMap). */
		return (key, sig) -> {
			if (key.hasDelegatableContractId()) {
				final var controllingId = key.getDelegatableContractIdKey().getContractID();
				return controllingId.equals(contractIdExpectedToBeSigned);
			} else if (key.hasContractID()) {
				final var controllingId = key.getContractIDKey().getContractID();
				return controllingId.equals(contractIdExpectedToBeSigned) && !isDelegateCall;
			} else {
				/* Otherwise delegate to the cryptographic validity test */
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

	private boolean isActiveInFrame(final JKey key, final Address recipient, final Address contract, final Address contractAddressExpectedToBeSigned) {
		final var pkToCryptoSigsFn = txnCtx.accessor().getRationalizedPkToCryptoSigFn();
		return activationTest.test(key, pkToCryptoSigsFn, validityTestFor(recipient, contract, contractAddressExpectedToBeSigned));
	}
}