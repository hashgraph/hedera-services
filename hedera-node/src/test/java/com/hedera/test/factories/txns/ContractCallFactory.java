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

import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.OptionalLong;


public class ContractCallFactory extends SignedTxnFactory<ContractCallFactory> {
	private OptionalLong gas = OptionalLong.empty();
	private OptionalLong sending = OptionalLong.empty();

	private ContractCallFactory() {}
	public static ContractCallFactory newSignedContractCall() {
		return new ContractCallFactory();
	}

	@Override
	protected ContractCallFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		ContractCallTransactionBody.Builder op = ContractCallTransactionBody.newBuilder();
		gas.ifPresent(op::setGas);
		sending.ifPresent(op::setAmount);
		txn.setContractCall(op);
	}

	public ContractCallFactory gas(long amount) {
		gas = OptionalLong.of(amount);
		return this;
	}

	public ContractCallFactory sending(long amount) {
		sending = OptionalLong.of(amount);
		return this;
	}
}
