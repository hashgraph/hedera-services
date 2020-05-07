package com.hedera.services.contracts.execution;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.keys.HederaKeyActivation;
import com.hedera.services.keys.SyncActivationCheck;
import com.hedera.services.sigs.PlatformSigOps;
import com.hedera.services.sigs.factories.BodySigningSigFactory;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.fcmap.FCMap;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.hedera.services.keys.HederaKeyActivation.ONLY_IF_SIG_IS_VALID;
import static com.hedera.services.keys.HederaKeyActivation.isActive;
import static com.hedera.services.sigs.sourcing.DefaultSigBytesProvider.DEFAULT_SIG_BYTES;
import static com.hedera.services.legacy.core.MapKey.getMapKey;
import static java.util.stream.Collectors.toList;

public class TxnAwareSoliditySigsVerifier implements SoliditySigsVerifier {
	private final SyncVerifier syncVerifier;
	private final TransactionContext txnCtx;
	private final SyncActivationCheck check;
	private final FCMap<MapKey, HederaAccount> accounts;

	public TxnAwareSoliditySigsVerifier(
			SyncVerifier syncVerifier,
			TransactionContext txnCtx,
			SyncActivationCheck check,
			FCMap<MapKey, HederaAccount> accounts
	) {
		this.txnCtx = txnCtx;
		this.accounts = accounts;
		this.syncVerifier = syncVerifier;
		this.check = check;
	}

	@Override
	public boolean allRequiredKeysAreActive(Set<AccountID> touched) {
		var payer = txnCtx.activePayer();
		var requiredKeys = touched.stream()
				.filter(id -> !payer.equals(id))
				.flatMap(this::keyRequirement)
				.collect(toList());
		if (requiredKeys.isEmpty()) {
			return true;
		} else {
			return check.allKeysAreActive(
					requiredKeys,
					syncVerifier,
					txnCtx.accessor(),
					PlatformSigOps::createEd25519PlatformSigsFrom,
					DEFAULT_SIG_BYTES::allPartiesSigBytesFor,
					BodySigningSigFactory::new,
					(key, sigsFn) -> isActive(key, sigsFn, ONLY_IF_SIG_IS_VALID),
					HederaKeyActivation::pkToSigMapFrom);
		}
	}

	private Stream<JKey> keyRequirement(AccountID id) {
		return Optional.ofNullable(accounts.get(getMapKey(id)))
				.filter(account -> !account.isSmartContract())
				.filter(HederaAccount::isReceiverSigRequired)
				.map(HederaAccount::getAccountKeys)
				.stream();
	}

//	private boolean syncKeysAreActive(List<JKey> keys, PlatformTxnAccessor accessor) {
//		var sigFactory = new BodySigningSigFactory(accessor.getTxnBytes());
//		var sigBytes = DEFAULT_SIG_BYTES.allPartiesSigBytesFor(accessor.getSignedTxn());
//
//		var creationResult = createEd25519PlatformSigsFrom(keys, sigBytes, sigFactory);
//		if (creationResult.hasFailed()) {
//			return false;
//		} else {
//			var sigs = creationResult.getPlatformSigs();
//			syncVerifier.verifySync(sigs);
//
//			var sigsFn = pkToSigMapFrom(sigs);
//			for (JKey key : keys) {
//				if (!isActive(key, sigsFn, ONLY_IF_SIG_IS_VALID)) {
//					return false;
//				}
//			}
//			return true;
//		}
//	}
}
