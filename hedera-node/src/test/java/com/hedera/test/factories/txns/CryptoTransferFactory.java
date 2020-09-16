package com.hedera.test.factories.txns;

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

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;

import java.util.List;

import static com.hedera.test.factories.txns.TinyBarsFromTo.tinyBarsFromTo;
import static java.util.stream.Collectors.*;
import static com.hedera.test.utils.IdUtils.asAccount;

public class CryptoTransferFactory extends SignedTxnFactory<CryptoTransferFactory> {
	public static final List<TinyBarsFromTo> DEFAULT_TRANSFERS = List.of(
			tinyBarsFromTo(DEFAULT_PAYER_ID, DEFAULT_NODE_ID, 1_000L));

	private List<TinyBarsFromTo> transfers = DEFAULT_TRANSFERS;

	private CryptoTransferFactory() {
	}

	public static CryptoTransferFactory newSignedCryptoTransfer() {
		return new CryptoTransferFactory();
	}

	public CryptoTransferFactory transfers(TinyBarsFromTo... transfers) {
		this.transfers = List.of(transfers);
		return this;
	}

	@Override
	protected CryptoTransferFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		CryptoTransferTransactionBody.Builder op = CryptoTransferTransactionBody.newBuilder();
		op.setTransfers(TransferList.newBuilder().addAllAccountAmounts(transfersAsAccountAmounts()));
		txn.setCryptoTransfer(op);
	}

	private List<AccountAmount> transfersAsAccountAmounts() {
		return transfers
				.stream()
				.flatMap(fromTo -> List.of(
						AccountAmount.newBuilder()
								.setAccountID(asAccount(fromTo.getPayer())).setAmount(-1 * fromTo.getAmount()).build(),
						AccountAmount.newBuilder()
								.setAccountID(asAccount(fromTo.getPayee())).setAmount(fromTo.getAmount()).build()
				).stream()).collect(toList());
	}
}
