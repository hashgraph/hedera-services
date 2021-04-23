package com.hedera.services.state.expiry;

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

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;

import java.time.Instant;

import static com.hedera.services.utils.EntityIdUtils.asLiteralString;
import static com.hedera.services.utils.MiscUtils.asTimestamp;

public class AutoRenewalRecord {
	public static TransactionRecord generatedFor(AccountID accountRenewed, Instant renewedAt,
			AccountID autoRenewAccount, long fee, long newExpirationTime, AccountID feeCollector) {
		TransactionReceipt receipt = TransactionReceipt.newBuilder().setAccountID(accountRenewed).build();
		TransactionID transactionID = TransactionID.newBuilder().setAccountID(autoRenewAccount).build();
		String memo = String.format("Entity %s was automatically renewed. New expiration time: %d.",
				asLiteralString(accountRenewed),
				newExpirationTime);
		AccountAmount payerAmount = AccountAmount.newBuilder()
				.setAccountID(autoRenewAccount)
				.setAmount(-1 * fee)
				.build();
		AccountAmount payeeAmount = AccountAmount.newBuilder()
				.setAccountID(feeCollector)
				.setAmount(fee)
				.build();
		TransferList transferList = TransferList.newBuilder()
				.addAccountAmounts(payerAmount)
				.addAccountAmounts(payeeAmount)
				.build();
		return TransactionRecord.newBuilder()
				.setReceipt(receipt)
				.setConsensusTimestamp(asTimestamp(renewedAt))
				.setTransactionID(transactionID)
				.setMemo(memo)
				.setTransactionFee(fee)
				.setTransferList(transferList)
				.build();
	}
}
