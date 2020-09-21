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
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiConsumer;

import static com.hedera.services.bdd.spec.assertions.AssertUtils.rethrowSummaryError;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;

import com.hedera.services.bdd.spec.assertions.ErroringAsserts;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

public class HapiGetAccountInfo extends HapiQueryOp<HapiGetAccountInfo> {
	private static final Logger log = LogManager.getLogger(HapiGetAccountInfo.class);

	private final String account;
	private List<String> absentRelationships = new ArrayList<>();
	private List<ExpectedTokenRel> relationships = new ArrayList<>();
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

	public HapiGetAccountInfo hasToken(ExpectedTokenRel relationship) {
		relationships.add(relationship);
		return this;
	}

	public HapiGetAccountInfo hasNoTokenRelationship(String token) {
		absentRelationships.add(token);
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
		for (ExpectedTokenRel rel : relationships) {
			var actualRels = response.getCryptoGetInfo().getAccountInfo().getTokenRelationshipsList();
			boolean found = false;
			var expectedId = spec.registry().getTokenID(rel.getToken());
			for (TokenRelationship actualRel : actualRels) {
				if (actualRel.getTokenId().equals(expectedId)) {
					found = true;
					rel.getBalance().ifPresent(a -> Assert.assertEquals(a, actualRel.getBalance()));
					rel.getKycStatus().ifPresent(s -> Assert.assertEquals(s, actualRel.getKycStatus()));
					rel.getFreezeStatus().ifPresent(s -> Assert.assertEquals(s, actualRel.getFreezeStatus()));
				}
			}
			if (!found) {
				Assert.fail(String.format(
						"Account '%s' had no relationship with token '%s'!",
						account,
						rel.getToken()));
			}
		}
		for (String unexpectedToken : absentRelationships) {
			var actualRels = response.getCryptoGetInfo().getAccountInfo().getTokenRelationshipsList();
			for (TokenRelationship actualRel : actualRels) {
				var unexpectedId = spec.registry().getTokenID(unexpectedToken);
				if (actualRel.getTokenId().equals(unexpectedId)) {
					Assert.fail(String.format(
							"Account '%s' should have had no relationship with token '%s'!",
							account,
							unexpectedToken));
				}
			}
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

	public static class ExpectedTokenRel {
		private final String token;

		private OptionalLong balance = OptionalLong.empty();
		private Optional<TokenKycStatus> kycStatus = Optional.empty();
		private Optional<TokenFreezeStatus> freezeStatus = Optional.empty();

		private ExpectedTokenRel(String token) {
			this.token = token;
		}

		public static ExpectedTokenRel relationshipWith(String token) {
			return new ExpectedTokenRel(token);
		}

		public ExpectedTokenRel balance(long expected) {
			balance = OptionalLong.of(expected);
			return this;
		}

		public ExpectedTokenRel kyc(TokenKycStatus expected) {
			kycStatus = Optional.of(expected);
			return this;
		}

		public ExpectedTokenRel freeze(TokenFreezeStatus expected) {
			freezeStatus = Optional.of(expected);
			return this;
		}

		public String getToken() {
			return token;
		}

		public OptionalLong getBalance() {
			return balance;
		}

		public Optional<TokenKycStatus> getKycStatus() {
			return kycStatus;
		}

		public Optional<TokenFreezeStatus> getFreezeStatus() {
			return freezeStatus;
		}
	}
}
