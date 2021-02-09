package com.hedera.services.bdd.spec.queries.token;

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
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenGetInfoQuery;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.BiFunction;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;

public class HapiGetTokenInfo extends HapiQueryOp<HapiGetTokenInfo> {
	private static final Logger log = LogManager.getLogger(HapiGetTokenInfo.class);

	String token;

	public HapiGetTokenInfo(String token) {
		this.token = token;
	}

	OptionalInt expectedDecimals = OptionalInt.empty();
	OptionalLong expectedTotalSupply = OptionalLong.empty();
	Optional<String> expectedId = Optional.empty();
	Optional<String> expectedSymbol = Optional.empty();
	Optional<String> expectedName = Optional.empty();
	Optional<String> expectedTreasury = Optional.empty();
	Optional<String> expectedAdminKey = Optional.empty();
	Optional<String> expectedKycKey = Optional.empty();
	Optional<String> expectedFreezeKey = Optional.empty();
	Optional<String> expectedSupplyKey = Optional.empty();
	Optional<String> expectedWipeKey = Optional.empty();
	Optional<Boolean> expectedDeletion = Optional.empty();
	Optional<TokenKycStatus> expectedKycDefault = Optional.empty();
	Optional<TokenFreezeStatus>	expectedFreezeDefault = Optional.empty();
	Optional<String> expectedAutoRenewAccount = Optional.empty();
	OptionalLong expectedAutoRenewPeriod = OptionalLong.empty();
	Optional<Boolean> expectedExpiry = Optional.empty();

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.TokenGetInfo;
	}

	@Override
	protected HapiGetTokenInfo self() {
		return this;
	}

	public HapiGetTokenInfo hasFreezeDefault(TokenFreezeStatus s) {
		expectedFreezeDefault = Optional.of(s);
		return this;
	}
	public HapiGetTokenInfo hasKycDefault(TokenKycStatus s) {
		expectedKycDefault = Optional.of(s);
		return this;
	}
	public HapiGetTokenInfo hasDecimals(int d) {
		expectedDecimals = OptionalInt.of(d);
		return this;
	}
	public HapiGetTokenInfo hasTotalSupply(long amount) {
		expectedTotalSupply = OptionalLong.of(amount);
		return this;
	}
	public HapiGetTokenInfo hasRegisteredId(String token) {
		expectedId = Optional.of(token);
		return this;
	}
	public HapiGetTokenInfo hasAutoRenewPeriod(Long renewPeriod) {
		expectedAutoRenewPeriod = OptionalLong.of(renewPeriod);
		return this;
	}
	public HapiGetTokenInfo hasAutoRenewAccount(String account) {
		expectedAutoRenewAccount = Optional.of(account);
		return this;
	}
	public HapiGetTokenInfo hasValidExpiry() {
		expectedExpiry = Optional.of(true);
		return this;
	}
	public HapiGetTokenInfo hasSymbol(String token) {
		expectedSymbol = Optional.of(token);
		return this;
	}
	public HapiGetTokenInfo hasName(String name) {
		expectedName = Optional.of(name);
		return this;
	}
	public HapiGetTokenInfo hasTreasury(String name) {
		expectedTreasury = Optional.of(name);
		return this;
	}
	public HapiGetTokenInfo hasFreezeKey(String name) {
		expectedFreezeKey = Optional.of(name);
		return this;
	}
	public HapiGetTokenInfo hasAdminKey(String name) {
		expectedAdminKey = Optional.of(name);
		return this;
	}
	public HapiGetTokenInfo hasKycKey(String name) {
		expectedKycKey = Optional.of(name);
		return this;
	}
	public HapiGetTokenInfo hasSupplyKey(String name) {
		expectedSupplyKey = Optional.of(name);
		return this;
	}
	public HapiGetTokenInfo hasWipeKey(String name) {
		expectedWipeKey = Optional.of(name);
		return this;
	}
	public HapiGetTokenInfo isDeleted() {
		expectedDeletion = Optional.of(Boolean.TRUE);
		return this;
	}
	public HapiGetTokenInfo isNotDeleted() {
		expectedDeletion = Optional.of(Boolean.FALSE);
		return this;
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
		var actualInfo = response.getTokenGetInfo().getTokenInfo();

		if (expectedSymbol.isPresent()) {
			Assert.assertEquals(
					"Wrong symbol!",
					expectedSymbol.get(),
					actualInfo.getSymbol());
		}

		if (expectedName.isPresent()) {
			Assert.assertEquals(
					"Wrong name!",
					expectedName.get(),
					actualInfo.getName());
		}

		if (expectedAutoRenewAccount.isPresent()) {
			var id = TxnUtils.asId(expectedAutoRenewAccount.get(), spec);
			Assert.assertEquals(
					"Wrong auto renew account!",
					id,
					actualInfo.getAutoRenewAccount());
		}

		if (expectedAutoRenewPeriod.isPresent()) {
			Assert.assertEquals(
					"Wrong auto renew period!",
					expectedAutoRenewPeriod.getAsLong(),
					actualInfo.getAutoRenewPeriod().getSeconds());
		}

		if (expectedTotalSupply.isPresent()) {
			Assert.assertEquals(
					"Wrong total supply!",
					expectedTotalSupply.getAsLong(),
					actualInfo.getTotalSupply());
		}

		if (expectedDecimals.isPresent()) {
			Assert.assertEquals(
					"Wrong decimals!",
					expectedDecimals.getAsInt(),
					actualInfo.getDecimals());
		}

		if (expectedTreasury.isPresent()) {
			var id = TxnUtils.asId(expectedTreasury.get(), spec);
			Assert.assertEquals(
					"Wrong treasury account!",
					id,
					actualInfo.getTreasury());
		}

		var registry = spec.registry();
		assertFor(
				actualInfo.getTokenId(),
				expectedId,
				(n, r) -> r.getTokenID(n),
				"Wrong token id!",
				registry);

		assertFor(
				actualInfo.getExpiry(),
				expectedExpiry,
				(n, r) -> Timestamp.newBuilder().setSeconds(r.getExpiry(token)).build(),
				"Wrong token expiry!",
				registry);

		assertFor(
				actualInfo.getFreezeKey(),
				expectedFreezeKey,
				(n, r) -> r.getFreezeKey(token),
				"Wrong token freeze key!",
				registry);

		assertFor(
				actualInfo.getAdminKey(),
				expectedAdminKey,
				(n, r) -> r.getAdminKey(token),
				"Wrong token admin key!",
				registry);

		assertFor(
				actualInfo.getWipeKey(),
				expectedWipeKey,
				(n, r) -> r.getWipeKey(token),
				"Wrong token wipe key!",
				registry);

		assertFor(
				actualInfo.getKycKey(),
				expectedKycKey,
				(n, r) -> r.getKycKey(token),
				"Wrong token KYC key!",
				registry);

		assertFor(
				actualInfo.getSupplyKey(),
				expectedSupplyKey,
				(n, r) -> r.getSupplyKey(token),
				"Wrong token supply key!",
				registry);
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
			Assert.assertEquals(error, expected, actual);
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
				.setToken(id)
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
