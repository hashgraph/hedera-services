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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceQuery;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;

public class HapiGetAccountBalance extends HapiQueryOp<HapiGetAccountBalance> {
	private static final Logger log = LogManager.getLogger(HapiGetAccountBalance.class);

	private Pattern DOT_DELIMTED_ACCOUNT = Pattern.compile("\\d+[.]\\d+[.]\\d+");
	private String entity;
	Optional<Long> expected = Optional.empty();
	Optional<Supplier<String>> entityFn = Optional.empty();
	Optional<Function<HapiApiSpec, Function<Long, Optional<String>>>> expectedCondition = Optional.empty();

	public HapiGetAccountBalance(String entity) {
		this.entity = entity;
	}

	public HapiGetAccountBalance(Supplier<String> supplier) {
		this.entityFn = Optional.of(supplier);
	}

	public HapiGetAccountBalance hasTinyBars(long amount) {
		expected = Optional.of(amount);
		return this;
	}
	public HapiGetAccountBalance hasTinyBars(Function<HapiApiSpec, Function<Long, Optional<String>>> condition) {
		expectedCondition = Optional.of(condition);
		return this;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.CryptoGetAccountBalance;
	}

	@Override
	protected HapiGetAccountBalance self() {
		return this;
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
		long actual = response.getCryptogetAccountBalance().getBalance();
		if (expectedCondition.isPresent()) {
			Function<Long, Optional<String>> condition = expectedCondition.get().apply(spec);
			Optional<String> failure = condition.apply(actual);
			if (failure.isPresent()) {
				Assert.fail("Bad balance! :: " + failure.get());
			}
		} else if (expected.isPresent()) {
			Assert.assertEquals("Wrong balance!", expected.get().longValue(), actual);
		}
	}

	@Override
	protected void submitWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getAccountBalanceQuery(spec, payment, false);
		response = spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls).cryptoGetBalance(query);
		ResponseCodeEnum status = response.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode();
		if (status == ResponseCodeEnum.ACCOUNT_DELETED) {
			log.info(spec.logPrefix() + entity + " was actually deleted!");
		} else {
			long balance = response.getCryptogetAccountBalance().getBalance();
			log.info(spec.logPrefix() + "balance for '" + entity + "': " + balance);
		}
	}

	private Query getAccountBalanceQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		if (entityFn.isPresent()) {
			entity = entityFn.get().get();
		}
		Consumer<CryptoGetAccountBalanceQuery.Builder> config;
		if (spec.registry().hasContractId(entity)) {
			config = b -> b.setContractID(spec.registry().getContractId(entity));
		} else {
			Matcher m = DOT_DELIMTED_ACCOUNT.matcher(entity);
			AccountID id = m.matches() ? HapiPropertySource.asAccount(entity) : spec.registry().getAccountID(entity);
			config = b -> b.setAccountID(id);
		}
		CryptoGetAccountBalanceQuery.Builder query = CryptoGetAccountBalanceQuery.newBuilder()
				.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment));
		config.accept(query);
		return Query.newBuilder().setCryptogetAccountBalance(query).build();
	}

	@Override
	protected boolean needsPayment() {
		return false;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		return super.toStringHelper().add("account", entity);
	}
}
