package com.hedera.services.statecreation.creationtxns;

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

import com.hedera.services.statecreation.creationtxns.utils.KeyFactory;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;

import java.util.Optional;

import static com.hedera.services.statecreation.creationtxns.utils.TempUtils.asAccount;

public class ScheduleCreateTxnFactory extends CreateTxnFactory<ScheduleCreateTxnFactory> {
	private boolean omitAdmin = false;
	private Optional<AccountID> payer = Optional.empty();

	private static Key adminKey = KeyFactory.getKey();

	private Optional<String> memo = Optional.empty();
	private long from;
	private long to;

	private ScheduleCreateTxnFactory() {
	}

	public static ScheduleCreateTxnFactory newSignedScheduleCreate() {
		return new ScheduleCreateTxnFactory();
	}


	public ScheduleCreateTxnFactory missingAdmin() {
		omitAdmin = true;
		return this;
	}

	public ScheduleCreateTxnFactory from(final long from) {
		this.from = from;
		return this;
	}
	public ScheduleCreateTxnFactory to(final long to) {
		this.to = to;
		return this;
	}

	public ScheduleCreateTxnFactory designatingPayer(AccountID id) {
		payer = Optional.of(id);
		return this;
	}

	public ScheduleCreateTxnFactory memo(String memoStr) {
		if(!memoStr.isEmpty()) {
			this.memo = Optional.of(memoStr);
		}
		return this;
	}

	@Override
	protected ScheduleCreateTxnFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		var op = ScheduleCreateTransactionBody.newBuilder();
		if (!omitAdmin) {
			op.setAdminKey(adminKey);
		}
		payer.ifPresent(op::setPayerAccountID);
		SchedulableTransactionBody scheduledTxn = getScheduledTxn(from, to);
		op.setScheduledTransactionBody(scheduledTxn);
		memo.ifPresent(txn::setMemo);
		txn.setScheduleCreate(op);
	}


	private SchedulableTransactionBody getScheduledTxn(final long from, final long to) {
		return SchedulableTransactionBody.newBuilder()
				.setTransactionFee(100_000_000L)
				.setMemo("default")
				.setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()
						.setTransfers(TransferList.newBuilder()
								.addAccountAmounts(AccountAmount.newBuilder()
										.setAccountID(asAccount("0.0." + from)).setAmount(1L).build())
								.addAccountAmounts(AccountAmount.newBuilder()
										.setAccountID(asAccount("0.0." + to)).setAmount(-1L).build())
								.build())
						.build())
				.build();
	}
}
