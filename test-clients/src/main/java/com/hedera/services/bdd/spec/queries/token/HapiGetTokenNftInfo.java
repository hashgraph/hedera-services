package com.hedera.services.bdd.spec.queries.token;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.TokenGetNftInfoQuery;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiFunction;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;

public class HapiGetTokenNftInfo extends HapiQueryOp<HapiGetTokenNftInfo> {
	private static final Logger log = LogManager.getLogger(HapiGetTokenNftInfo.class);

	String token;
	long serialNum;

	public HapiGetTokenNftInfo(String token, long serialNum) {
		this.token = token;
		this.serialNum = serialNum;
	}

	OptionalLong expectedSerialNum = OptionalLong.empty();
	Optional<ByteString> expectedMetadata = Optional.empty();
	Optional<String> expectedTokenID = Optional.empty();
	Optional<String> expectedAccountID = Optional.empty();
	Optional<Boolean> expectedCreationTime = Optional.empty();

	public HapiGetTokenNftInfo hasAccountID(String name) {
		expectedAccountID = Optional.of(name);
		return this;
	}

	public HapiGetTokenNftInfo hasTokenID(String token) {
		expectedTokenID = Optional.of(token);
		return this;
	}

	public HapiGetTokenNftInfo hasSerialNum(long serialNum) {
		expectedSerialNum = OptionalLong.of(serialNum);
		return this;
	}

	public HapiGetTokenNftInfo hasMetadata(ByteString metadata) {
		expectedMetadata = Optional.of(metadata);
		return this;
	}

	public HapiGetTokenNftInfo hasValidCreationTime() {
		expectedCreationTime = Optional.of(true);
		return this;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.TokenGetNftInfo;
	}

	@Override
	protected HapiGetTokenNftInfo self() {
		return this;
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
		var actualInfo = response.getTokenGetNftInfo().getNft();

		if (expectedSerialNum.isPresent()) {
			Assertions.assertEquals(
					expectedSerialNum.getAsLong(),
					actualInfo.getNftID().getSerialNumber(),
					"Wrong serial num!");
		}

		if (expectedAccountID.isPresent()) {
			var id = TxnUtils.asId(expectedAccountID.get(), spec);
			Assertions.assertEquals(
					id,
					actualInfo.getAccountID(),
					"Wrong account ID account!");
		}

		expectedMetadata.ifPresent(bytes -> Assertions.assertEquals(
				bytes,
				actualInfo.getMetadata(),
				"Wrong metadata!"));

		assertFor(
				actualInfo.getCreationTime(),
				expectedCreationTime,
				(n, r) -> r.getCreationTime(token),
				"Wrong creation time (seconds)!",
				spec.registry());

		var registry = spec.registry();
		assertFor(
				actualInfo.getNftID().getTokenID(),
				expectedTokenID,
				(n, r) -> r.getTokenID(n),
				"Wrong token id!",
				registry);

		Assertions.assertEquals(ByteString.copyFromUtf8(expectedLedgerId), actualInfo.getLedgerId());
	}

	private <T, R> void assertFor(
			R actual,
			Optional<T> possible,
			BiFunction<T, HapiSpecRegistry, R> expectedFn,
			String error,
			HapiSpecRegistry registry
	) {
		if (possible.isPresent()) {
			var expected = expectedFn.apply(possible.get(), registry);
			Assertions.assertEquals(expected, actual, error);
		}
	}

	@Override
	protected void submitWith(HapiApiSpec spec, Transaction payment) {
		Query query = getTokenNftInfoQuery(spec, payment, false);
		response = spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls).getTokenNftInfo(query);
		if (verboseLoggingOn) {
			log.info("Info for '" + token + "': " + response.getTokenGetNftInfo().getNft());
		}
	}

	@Override
	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getTokenNftInfoQuery(spec, payment, true);
		Response response = spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls).getTokenNftInfo(query);
		return costFrom(response);
	}

	private Query getTokenNftInfoQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		var id = TxnUtils.asTokenId(token, spec);
		TokenGetNftInfoQuery getTokenNftQuery = TokenGetNftInfoQuery.newBuilder()
				.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
				.setNftID(NftID.newBuilder().setTokenID(id).setSerialNumber(serialNum).build())
				.build();
		return Query.newBuilder().setTokenGetNftInfo(getTokenNftQuery).build();
	}

	@Override
	protected boolean needsPayment() {
		return true;
	}

}
