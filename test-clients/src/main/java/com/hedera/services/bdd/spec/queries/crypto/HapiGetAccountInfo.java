package com.hedera.services.bdd.spec.queries.crypto;

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
import com.hederahashgraph.api.proto.java.CryptoGetInfoQuery;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.hedera.services.bdd.spec.assertions.AssertUtils.rethrowSummaryError;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;

import com.hedera.services.bdd.spec.assertions.ErroringAsserts;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiGetAccountInfo extends HapiQueryOp<HapiGetAccountInfo> {
	private static final Logger log = LogManager.getLogger(HapiGetAccountInfo.class);

	private final String account;
	Optional<AccountInfoAsserts> expectations = Optional.empty();
	Optional<BiConsumer<AccountInfo, Logger>> customLog = Optional.empty();

	public HapiGetAccountInfo(String account) {
		this.account = account;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.CryptoGetInfo;
	}

	public HapiGetAccountInfo has(AccountInfoAsserts provider) {
		expectations = Optional.of(provider);
		return this;
	}
	public HapiGetAccountInfo plusCustomLog(BiConsumer<AccountInfo, Logger> custom) {
		customLog = Optional.of(custom);
		return this;
	}

	@Override
	protected HapiGetAccountInfo self() {
		return this;
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
		if (expectations.isPresent()) {
			AccountInfo actualInfo = response.getCryptoGetInfo().getAccountInfo();
			ErroringAsserts<AccountInfo> asserts = expectations.get().assertsFor(spec);
			List<Throwable> errors = asserts.errorsIn(actualInfo);
			rethrowSummaryError(log, "Bad account info!", errors);
		}
	}

	@Override
	protected void submitWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getAccountInfoQuery(spec, payment, false);
		response = spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls).getAccountInfo(query);
		if (verboseLoggingOn) {
			log.info("Info for '" + account + "': " + response.getCryptoGetInfo().getAccountInfo());
		}
		if (customLog.isPresent()) {
			customLog.get().accept(response.getCryptoGetInfo().getAccountInfo(), log);
		}
	}

	@Override
	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getAccountInfoQuery(spec, payment, true);
		Response response = spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls).getAccountInfo(query);
		return costFrom(response);
	}

	private Query getAccountInfoQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		CryptoGetInfoQuery query = CryptoGetInfoQuery.newBuilder()
				.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
				.setAccountID(TxnUtils.asId(account, spec))
				.build();
		return Query.newBuilder().setCryptoGetInfo(query).build();
	}

	@Override
	protected boolean needsPayment() {
		return true;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		return super.toStringHelper().add("account", account);
	}
}
