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
import com.hedera.services.keys.HederaKeyActivation;
import com.hedera.services.keys.SyncActivationCheck;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.PlatformSigOps;
import com.hedera.services.sigs.factories.BodySigningSigFactory;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.merkle.map.MerkleMap;
import org.hyperledger.besu.datatypes.Address;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static com.hedera.services.keys.HederaKeyActivation.ONLY_IF_SIG_IS_VALID;
import static com.hedera.services.keys.HederaKeyActivation.isActive;
import static com.hedera.services.utils.EntityIdUtils.contractParsedFromSolidityAddress;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.swirlds.common.crypto.VerificationStatus.VALID;

public class TxnAwareSoliditySigsVerifier implements SoliditySigsVerifier {
	private final SyncVerifier syncVerifier;
	private final TransactionContext txnCtx;
	private final SyncActivationCheck check;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;

	public TxnAwareSoliditySigsVerifier(
			SyncVerifier syncVerifier,
			TransactionContext txnCtx,
			SyncActivationCheck check,
			Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts
	) {
		this.txnCtx = txnCtx;
		this.accounts = accounts;
		this.syncVerifier = syncVerifier;
		this.check = check;
	}

	@Override
	public boolean hasActiveKeyOrNoReceiverSigReq(
			final Address target,
			final Address recipient,
			final Address contract
	) {
		final var targetId = EntityIdUtils.accountParsedFromSolidityAddress(target);
		final var payer = txnCtx.activePayer();
		if (payer.equals(targetId)) {
			return true;
		}
		final var requiredKey = receiverSigKey(targetId);
		if (requiredKey.isPresent()) {
			final var accessor = txnCtx.accessor();
			return check.allKeysAreActive(
					List.of(requiredKey.get()),
					syncVerifier,
					accessor,
					PlatformSigOps::createEd25519PlatformSigsFrom,
					accessor.getPkToSigsFn(),
					BodySigningSigFactory::new,
					(key, sigsFn) -> isActive(key, sigsFn, ONLY_IF_SIG_IS_VALID),
					HederaKeyActivation::pkToSigMapFrom);
		} else {
			return true;
		}
	}

	BiPredicate<JKey, TransactionSignature> validityTestFor(final Address recipient, final Address contract) {
		final var activeId = contractParsedFromSolidityAddress(recipient);
		final var isDelegateCall = !contract.equals(recipient);

		/* Note that when this observer is used directly above in isActive(), it will be called
		 * with each primitive key in the top-level Hedera key of interest, along with that key's
		 * verified cryptographic signature (if any was available in the sigMap). */
		return (primitiveKey, verifiedSig) -> {
			if (primitiveKey.hasDelegateContractID()) {
				final var controllingId = primitiveKey.getDelegateContractIDKey().getContractID();
				return controllingId.equals(activeId);
			} else if (primitiveKey.hasContractID()) {
				final var controllingId = primitiveKey.getContractIDKey().getContractID();
				return controllingId.equals(activeId) && !isDelegateCall;
			} else {
				/* Here we can ignore the key; all that matters is whether its signature was valid. */
				return 	verifiedSig.getSignatureStatus() == VALID;
			}
		};
	}

	private Optional<JKey> receiverSigKey(final AccountID id) {
		return Optional.ofNullable(accounts.get().get(fromAccountId(id)))
				.filter(account -> !account.isSmartContract())
				.filter(MerkleAccount::isReceiverSigRequired)
				.map(MerkleAccount::getAccountKey);
	}
}
