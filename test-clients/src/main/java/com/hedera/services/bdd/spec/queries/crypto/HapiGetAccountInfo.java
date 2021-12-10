package com.hedera.services.bdd.spec.queries.crypto;

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

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.assertions.ErroringAsserts;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.CryptoGetInfoQuery;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

import static com.hedera.services.bdd.spec.assertions.AssertUtils.rethrowSummaryError;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class HapiGetAccountInfo extends HapiQueryOp<HapiGetAccountInfo> {
	private static final Logger log = LogManager.getLogger(HapiGetAccountInfo.class);

	private String account;
	private ByteString alias = ByteString.EMPTY;
	private Optional<String> registryEntry = Optional.empty();
	private List<String> absentRelationships = new ArrayList<>();
	private List<ExpectedTokenRel> relationships = new ArrayList<>();
	Optional<AccountInfoAsserts> expectations = Optional.empty();
	Optional<BiConsumer<AccountInfo, Logger>> customLog = Optional.empty();
	Optional<LongConsumer> exposingExpiryTo = Optional.empty();
	Optional<LongConsumer> exposingBalanceTo = Optional.empty();
	Optional<Long> ownedNfts = Optional.empty();
	Optional<Integer> maxAutomaticAssociations = Optional.empty();
	Optional<Integer> alreadyUsedAutomaticAssociations = Optional.empty();

	public HapiGetAccountInfo(String account) {
		this.account = account;
	}
	public HapiGetAccountInfo(ByteString alias) {
		this.account = "0.0.0";
		this.alias = alias;
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

	public HapiGetAccountInfo exposingExpiry(LongConsumer obs) {
		this.exposingExpiryTo = Optional.of(obs);
		return this;
	}

	public HapiGetAccountInfo exposingBalance(LongConsumer obs) {
		this.exposingBalanceTo = Optional.of(obs);
		return this;
	}

	public HapiGetAccountInfo savingSnapshot(String registryEntry) {
		this.registryEntry = Optional.of(registryEntry);
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

	public HapiGetAccountInfo hasOwnedNfts(long ownedNftsLen) {
		this.ownedNfts = Optional.of(ownedNftsLen);
		return this;
	}

	public HapiGetAccountInfo hasMaxAutomaticAssociations(int max) {
		this.maxAutomaticAssociations = Optional.of(max);
		return this;
	}

	public HapiGetAccountInfo hasAlreadyUsedAutomaticAssociations(int count) {
		this.alreadyUsedAutomaticAssociations = Optional.of(count);
		return this;
	}

	@Override
	protected HapiGetAccountInfo self() {
		return this;
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
		final var actualInfo = response.getCryptoGetInfo().getAccountInfo();
		if (expectations.isPresent()) {
			ErroringAsserts<AccountInfo> asserts = expectations.get().assertsFor(spec);
			List<Throwable> errors = asserts.errorsIn(actualInfo);
			rethrowSummaryError(log, "Bad account info!", errors);
		}
		var actualTokenRels = actualInfo.getTokenRelationshipsList();
		ExpectedTokenRel.assertExpectedRels(account, relationships, actualTokenRels, spec);
		ExpectedTokenRel.assertNoUnexpectedRels(account, absentRelationships, actualTokenRels, spec);

		var actualOwnedNfts = actualInfo.getOwnedNfts();
		ownedNfts.ifPresent(nftsOwned -> Assertions.assertEquals((long) nftsOwned, actualOwnedNfts));

		var actualMaxAutoAssociations = actualInfo.getMaxAutomaticTokenAssociations();
		maxAutomaticAssociations.ifPresent(maxAutoAssociations ->
				Assertions.assertEquals((int) maxAutoAssociations, actualMaxAutoAssociations));
		alreadyUsedAutomaticAssociations.ifPresent(usedCount -> {
			int actualCount = 0;
			for (var rel : actualTokenRels) {
				if (rel.getAutomaticAssociation()) {
					actualCount++;
				}
			}
			Assertions.assertEquals(actualCount, usedCount);
		});
	}

	@Override
	protected void submitWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getAccountInfoQuery(spec, payment, false);
		response = spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls).getAccountInfo(query);
		final var infoResponse = response.getCryptoGetInfo();
		if (infoResponse.getHeader().getNodeTransactionPrecheckCode() == OK) {
			exposingExpiryTo.ifPresent(cb -> cb.accept(infoResponse.getAccountInfo().getExpirationTime().getSeconds()));
			exposingBalanceTo.ifPresent(cb -> cb.accept(infoResponse.getAccountInfo().getBalance()));
		}
		if (verboseLoggingOn) {
			log.info("Info for '" + account + "': " + response.getCryptoGetInfo().getAccountInfo());
		}
		if (customLog.isPresent()) {
			customLog.get().accept(response.getCryptoGetInfo().getAccountInfo(), log);
		}
		if (registryEntry.isPresent()) {
			spec.registry().saveAccountInfo(registryEntry.get(), response.getCryptoGetInfo().getAccountInfo());
		}
	}

	@Override
	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getAccountInfoQuery(spec, payment, true);
		Response response = spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls).getAccountInfo(query);
		return costFrom(response);
	}

	private Query getAccountInfoQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		if (!alias.isEmpty()) {
			account = alias.toStringUtf8();
		}
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
