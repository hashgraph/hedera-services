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

import com.hedera.test.factories.keys.KeyTree;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.OptionalLong;

import static com.hedera.test.factories.keys.NodeFactory.ed25519;

public class ContractCreateFactory extends SignedTxnFactory<ContractCreateFactory> {
	public static final KeyTree DEFAULT_ADMIN_KT = KeyTree.withRoot(ed25519());
	public static final Key DEPRECATED_CID_KEY =
			Key.newBuilder().setContractID(ContractID.newBuilder().setContractNum(1234L).build()).build();

	private KeyTree adminKt = DEFAULT_ADMIN_KT;
	private boolean useAdminKey = true;
	private boolean useDeprecatedAdminKey = false;
	private OptionalLong gas = OptionalLong.empty();
	private OptionalLong initialBalance = OptionalLong.empty();

	private ContractCreateFactory() {}
	public static ContractCreateFactory newSignedContractCreate() {
		return new ContractCreateFactory();
	}

	@Override
	protected ContractCreateFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		ContractCreateTransactionBody.Builder op = ContractCreateTransactionBody.newBuilder();
		if (useDeprecatedAdminKey) {
			op.setAdminKey(DEPRECATED_CID_KEY);
		} else if (useAdminKey) {
			op.setAdminKey(adminKt.asKey(keyFactory));
		}
		gas.ifPresent(op::setGas);
		initialBalance.ifPresent(op::setInitialBalance);
		txn.setContractCreateInstance(op);
	}

	public ContractCreateFactory gas(long amount) {
		gas = OptionalLong.of(amount);
		return this;
	}

	public ContractCreateFactory initialBalance(long amount) {
		initialBalance = OptionalLong.of(amount);
		return this;
	}

	public ContractCreateFactory adminKt(KeyTree adminKt) {
		this.adminKt = adminKt;
		return this;
	}

	public ContractCreateFactory useDeprecatedAdminKey(boolean shouldUse) {
		useDeprecatedAdminKey = shouldUse;
		return this;
	}
	public ContractCreateFactory useAdminKey(boolean shouldUse) {
		useAdminKey = shouldUse;
		return this;
	}
}
