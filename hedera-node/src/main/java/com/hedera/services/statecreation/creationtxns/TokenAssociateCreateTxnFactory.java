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

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TokenAssociateCreateTxnFactory extends CreateTxnFactory<TokenAssociateCreateTxnFactory> {
	Map<TokenID, List<AccountAmount>> adjustments = new HashMap<>();

	private AccountID target;
	private List<TokenID> associations = new ArrayList<>();

	private TokenAssociateCreateTxnFactory() {
	}

	public static TokenAssociateCreateTxnFactory newSignedTokenAssociate() {
		return new TokenAssociateCreateTxnFactory();
	}

	public TokenAssociateCreateTxnFactory targeting(AccountID target) {
		this.target = target;
		return this;
	}

	public TokenAssociateCreateTxnFactory associating(TokenID token) {
		associations.add(token);
		return this;
	}

	public TokenAssociateCreateTxnFactory targeting(final long accountNum) {
		AccountID accountID = AccountID.newBuilder().setAccountNum(accountNum).setRealmNum(0).setShardNum(0)
				.build();
		this.target = accountID;
		return this;
	}

	public TokenAssociateCreateTxnFactory associating(final long... tokenNums) {
		for(long num: tokenNums) {
			TokenID token = TokenID.newBuilder()
					.setTokenNum(num).setRealmNum(0L).setShardNum(0L).build();
			associations.add(token);
		}
		return this;
	}

	@Override
	protected TokenAssociateCreateTxnFactory self() {
		return this;
	}

	@Override
	protected long feeFor(Transaction signedTxn, int numPayerKeys) {
		return 0;
	}

	@Override
	protected void customizeTxn(TransactionBody.Builder txn) {
		txn.setTokenAssociate(TokenAssociateTransactionBody.newBuilder()
				.setAccount(target)
				.addAllTokens(associations))
				.build();
	}
}
