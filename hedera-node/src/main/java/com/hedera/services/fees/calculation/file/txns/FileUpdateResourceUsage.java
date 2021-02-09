package com.hedera.services.fees.calculation.file.txns;

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
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.fees.calculation.UsageEstimatorUtils.changeInSbsUsage;
import static com.hedera.services.fees.calculation.UsageEstimatorUtils.defaultPartitioning;
import static com.hedera.services.fees.calculation.UsageEstimatorUtils.nonDegenerateDiv;
import static com.hedera.services.fees.calculation.UsageEstimatorUtils.withBaseTxnUsage;
import static com.hedera.services.fees.calculation.UsageEstimatorUtils.zeroedComponents;
import static com.hederahashgraph.fee.FeeBuilder.*;
import static com.hedera.services.fees.calculation.UsageEstimatorUtils.keyBytes;

public class FileUpdateResourceUsage implements TxnResourceUsageEstimator {
	public static final Logger log = LogManager.getLogger(FileUpdateResourceUsage.class);

	@Override
	public boolean applicableTo(TransactionBody txn) {
		return txn.hasFileUpdate();
	}

	@Override
	public FeeData usageGiven(TransactionBody txn, SigValueObj sigUsage, StateView view) {
		var components = withOpUsage(
				withBaseTxnUsage(zeroedComponents(), sigUsage, txn),
				txn.getFileUpdate(),
				view,
				txn.getTransactionID().getTransactionValidStart().getSeconds());
		return defaultPartitioning(components.build(), sigUsage.getPayerAcctSigCount());
	}

	FeeComponents.Builder withOpUsage(
			FeeComponents.Builder components,
			FileUpdateTransactionBody op,
			StateView view,
			long at
	) {
		long newKeyBytes, newContentBytes, newSecs;
		long oldKeyBytes = 0, oldContentBytes = 0, oldSecs = 0;
		var info = view.infoForFile(op.getFileID());
		if (info.isPresent()) {
			var details = info.get();
			oldSecs = details.getExpirationTime().getSeconds() - at;
			oldContentBytes = details.getSize();
			oldKeyBytes = keyBytes(details.getKeys().getKeysList());
		}
		newSecs = op.getExpirationTime().getSeconds() - at;
		newKeyBytes = op.hasKeys() ? keyBytes(op.getKeys().getKeysList()) : oldKeyBytes;
		newContentBytes = (op.getContents().size() > 0) ? op.getContents().size() : oldContentBytes;
		long oldBytes = (oldKeyBytes + oldContentBytes), newBytes = (newKeyBytes + newContentBytes);

		long sbhDelta = nonDegenerateDiv(changeInSbsUsage(oldBytes, oldSecs, newBytes, newSecs), HRS_DIVISOR);
		components.setSbh(sbhDelta);
		components.setBpt(components.getBpt() + opBytes(op));

		return components;
	}

	int opBytes(FileUpdateTransactionBody op) {
		return BASIC_ENTITY_ID_SIZE
				+ keyBytes(op.getKeys().getKeysList())
				+ (op.hasExpirationTime() ? LONG_SIZE : 0)
				+ op.getContents().size();
	}
}
