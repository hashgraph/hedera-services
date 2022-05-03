package com.hedera.services.fees.calculation.contract.txns;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.services.fees.calculation.FeeCalcUtils.lookupAccountExpiry;
import static com.hedera.services.utils.EntityNum.fromContractId;

@Singleton
public class ContractUpdateResourceUsage implements TxnResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(ContractUpdateResourceUsage.class);

	private final SmartContractFeeBuilder usageEstimator;

	@Inject
	public ContractUpdateResourceUsage(SmartContractFeeBuilder usageEstimator) {
		this.usageEstimator = usageEstimator;
	}

	@Override
	public boolean applicableTo(TransactionBody txn) {
		return txn.hasContractUpdateInstance();
	}

	@Override
	public FeeData usageGiven(TransactionBody txn, SigValueObj sigUsage, StateView view) throws InvalidTxBodyException {
		try {
			final var id = fromContractId(txn.getContractUpdateInstance().getContractID());
			Timestamp expiry = lookupAccountExpiry(id, view.accounts());
			int oldMaxAutoAssociationSlots = lookUpAccountAutoAssociationSlots(id, view.accounts());
			return usageEstimator.getContractUpdateTxFeeMatrices(txn, expiry, sigUsage, oldMaxAutoAssociationSlots);
		} catch (Exception e) {
			log.debug("Unable to deduce ContractUpdate usage for {}, using defaults", txn.getTransactionID(), e);
			return FeeData.getDefaultInstance();
		}
	}

	private int lookUpAccountAutoAssociationSlots(final EntityNum key,
			final MerkleMap<EntityNum, MerkleAccount> accounts) {
		try {
			final var account = accounts.get(key);
			return account.getMaxAutomaticAssociations();
		} catch (Exception ignore) {
			return 0;
		}
	}
}
