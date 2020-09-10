package com.hedera.services.bdd.spec.transactions.token;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenWipeAccount;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class HapiTokenWipe extends HapiTxnOp<HapiTokenWipe> {
	static final Logger log = LogManager.getLogger(HapiTokenWipe.class);

	private String account;
	private String token;

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.TokenAccountWipe;
	}

	public HapiTokenWipe(String token, String account) {
		this.token = token;
		this.account = account;
	}

	@Override
	protected HapiTokenWipe self() {
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.TokenAccountWipe, this::mockTokenWipeUsage, txn, numPayerKeys);
	}

	private FeeData mockTokenWipeUsage(TransactionBody ignoredTxn, SigValueObj ignoredSigUsage) {
		return TxnUtils.defaultPartitioning(
				FeeComponents.newBuilder()
						.setMin(1)
						.setMax(1_000_000)
						.setConstant(1)
						.setBpt(1)
						.setVpt(1)
						.setRbh(1)
						.setSbh(1)
						.setGas(1)
						.setTv(1)
						.setBpr(1)
						.setSbpr(1)
						.build(), 1);
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		var tId = TxnUtils.asTokenId(token, spec);
		var aId = TxnUtils.asId(account, spec);
		TokenWipeAccount opBody = spec
				.txns()
				.<TokenWipeAccount, TokenWipeAccount.Builder>body(
						TokenWipeAccount.class, b -> {
							b.setToken(TxnUtils.asRef(tId));
							b.setAccount(aId);
						});
		return b -> b.setTokenWipe(opBody);
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		return List.of(
				spec -> spec.registry().getKey(effectivePayer(spec)),
				spec -> spec.registry().getWipeKey(token));
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::wipeTokenAccount;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("token", token)
				.add("account", account);
		return helper;
	}
}
