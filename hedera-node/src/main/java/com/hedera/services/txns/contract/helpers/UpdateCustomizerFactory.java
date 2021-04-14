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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;

public class UpdateCustomizerFactory {
	public Pair<Optional<HederaAccountCustomizer>, ResponseCodeEnum> customizerFor(
			MerkleAccount contract,
			ContractUpdateTransactionBody updateOp
	) {
		throw new AssertionError("Not implemented!");
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
}
