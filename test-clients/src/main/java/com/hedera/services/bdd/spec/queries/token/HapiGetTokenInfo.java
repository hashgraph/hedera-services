package com.hedera.services.bdd.spec.queries.token;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Token 2.0 (the "License");
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
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.TokenGetInfoQuery;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.Optional;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;

public class HapiGetTokenInfo extends HapiQueryOp<HapiGetTokenInfo> {
	private static final Logger log = LogManager.getLogger(HapiGetTokenInfo.class);

	String token;

	public HapiGetTokenInfo(String token) {
		this.token = token;
	}

	Optional<String> expectedId = Optional.empty();

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.TokenGetInfo;
	}

	@Override
	protected HapiGetTokenInfo self() {
		return this;
	}

	public HapiGetTokenInfo hasTokenId(String token) {
		expectedId = Optional.of(token);
		return this;
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
		var actualInfo = response.getTokenGetInfo().getTokenInfo();
		if (expectedId.isPresent()) {
			var expected = spec.registry().getTokenID(expectedId.get());
			Assert.assertEquals("Wrong TokenID", expected, actualInfo.getTokenId());
		}
	}

	@Override
	protected void submitWith(HapiApiSpec spec, Transaction payment) {
		Query query = getTokenInfoQuery(spec, payment, false);
		response = spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls).getTokenInfo(query);
		if (verboseLoggingOn) {
			log.info("Info for '" + token + "': " + response.getTokenGetInfo().getTokenInfo());
		}
	}

	@Override
	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getTokenInfoQuery(spec, payment, true);
		Response response = spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls).getTokenInfo(query);
		return costFrom(response);
	}

	private Query getTokenInfoQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		var id = TxnUtils.asTokenId(token, spec);
		TokenGetInfoQuery getTokenQuery = TokenGetInfoQuery.newBuilder()
				.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
				.setToken(TokenRef.newBuilder().setTokenId(id))
				.build();
		return Query.newBuilder().setTokenGetInfo(getTokenQuery).build();
	}

	@Override
	protected boolean needsPayment() {
		return true;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		return MoreObjects.toStringHelper(this).add("token", token);
	}
}
