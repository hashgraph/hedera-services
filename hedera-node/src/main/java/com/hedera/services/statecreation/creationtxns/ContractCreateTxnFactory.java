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
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.OptionalLong;
import java.util.Optional;

public class ContractCreateTxnFactory extends CreateTxnFactory<ContractCreateTxnFactory> {
	public static final Key DEPRECATED_CID_KEY =
			Key.newBuilder().setContractID(ContractID.newBuilder().setContractNum(1234L).build()).build();

	private Key adminKey = KeyFactory.getKey();
	private boolean useAdminKey = true;
	private boolean useDeprecatedAdminKey = false;
	private OptionalLong gas = OptionalLong.empty();
	private OptionalLong initialBalance = OptionalLong.empty();
	private Optional<FileID> contractFileID = Optional.of(FileID.getDefaultInstance());


	private ContractCreateTxnFactory() {}
	public static ContractCreateTxnFactory newSignedContractCreate() {
		return new ContractCreateTxnFactory();
	}

	@Override
	protected ContractCreateTxnFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		ContractCreateTransactionBody.Builder op = ContractCreateTransactionBody.newBuilder();
		Duration.Builder duration = Duration.newBuilder()
				.setSeconds(30000);
		op.setAutoRenewPeriod(duration);
		op.setFileID(contractFileID.get());

		if (useDeprecatedAdminKey) {
			op.setAdminKey(DEPRECATED_CID_KEY);
		} else if (useAdminKey) {
			op.setAdminKey(adminKey);
		}
		gas.ifPresent(op::setGas);
		initialBalance.ifPresent(op::setInitialBalance);
		txn.setContractCreateInstance(op);
	}

	public ContractCreateTxnFactory gas(long amount) {
		gas = OptionalLong.of(amount);
		return this;
	}

	public ContractCreateTxnFactory initialBalance(long amount) {
		initialBalance = OptionalLong.of(amount);
		return this;
	}

	public ContractCreateTxnFactory useDeprecatedAdminKey(boolean shouldUse) {
		useDeprecatedAdminKey = shouldUse;
		return this;
	}
	public ContractCreateTxnFactory fileID(FileID fileID) {
		contractFileID = Optional.of(fileID);
		return this;
	}

	public ContractCreateTxnFactory useAdminKey(boolean shouldUse) {
		useAdminKey = shouldUse;
		return this;
	}
}
