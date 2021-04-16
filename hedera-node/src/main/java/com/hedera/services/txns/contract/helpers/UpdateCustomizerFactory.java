package com.hedera.services.txns.contract.helpers;

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

import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;

import static com.hedera.services.sigs.utils.ImmutableKeyUtils.IMMUTABILITY_SENTINEL_KEY;
import static com.hedera.services.state.submerkle.EntityId.fromGrpcAccountId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class UpdateCustomizerFactory {
	public Pair<Optional<HederaAccountCustomizer>, ResponseCodeEnum> customizerFor(
			MerkleAccount contract,
			ContractUpdateTransactionBody updateOp
	) {
		if (!onlyAffectsExpiry(updateOp) && !isMutable(contract)) {
			return Pair.of(Optional.empty(), MODIFYING_IMMUTABLE_CONTRACT);
		}

		if (reducesExpiry(updateOp, contract.getExpiry())) {
			return Pair.of(Optional.empty(), EXPIRATION_REDUCTION_NOT_ALLOWED);
		}

		var cid = updateOp.getContractID();
		var customizer = new HederaAccountCustomizer();
		if (updateOp.hasAdminKey()) {
			if (IMMUTABILITY_SENTINEL_KEY.equals(updateOp.getAdminKey())) {
				customizer.key(new JContractIDKey(cid.getShardNum(), cid.getRealmNum(), cid.getContractNum()));
			} else {
				var resolution = keyIfAcceptable(updateOp.getAdminKey());
				if (resolution.isEmpty()) {
					return Pair.of(Optional.empty(), INVALID_ADMIN_KEY);
				}
				customizer.key(resolution.get());
			}
		}
		if (updateOp.hasProxyAccountID()) {
			customizer.proxy(fromGrpcAccountId(updateOp.getProxyAccountID()));
		}
		if (updateOp.hasAutoRenewPeriod()) {
			customizer.autoRenewPeriod(updateOp.getAutoRenewPeriod().getSeconds());
		}
		if (updateOp.hasExpirationTime()) {
			customizer.expiry(updateOp.getExpirationTime().getSeconds());
		}
		if (affectsMemo(updateOp)) {
			if (updateOp.hasMemoWrapper()) {
				customizer.memo(updateOp.getMemoWrapper().getValue());
			} else {
				customizer.memo(updateOp.getMemo());
			}
		}

		return Pair.of(Optional.of(customizer), OK);
	}

	boolean isMutable(MerkleAccount contract) {
		return Optional.ofNullable(contract.getKey()).map(key -> !key.hasContractID()).orElse(false);
	}

	boolean onlyAffectsExpiry(ContractUpdateTransactionBody op) {
		return !(op.hasProxyAccountID()
				|| op.hasFileID()
				|| affectsMemo(op)
				|| op.hasAutoRenewPeriod()
				|| op.hasAdminKey());
	}

	boolean affectsMemo(ContractUpdateTransactionBody op) {
		return op.hasMemoWrapper() || op.getMemo().length() > 0;
	}

	private boolean reducesExpiry(ContractUpdateTransactionBody op, long curExpiry) {
		return op.hasExpirationTime() && op.getExpirationTime().getSeconds() < curExpiry;
	}

	private Optional<JKey> keyIfAcceptable(Key candidate) {
		var key = MiscUtils.asUsableFcKey(candidate);
		if (key.isEmpty() || key.get() instanceof JContractIDKey) {
			return Optional.empty();
		}
		return key;
	}
}
