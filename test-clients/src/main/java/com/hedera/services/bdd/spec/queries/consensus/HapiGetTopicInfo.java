package com.hedera.services.bdd.spec.queries.consensus;

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
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoQuery;
import com.hederahashgraph.api.proto.java.ConsensusTopicInfo;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static org.junit.Assert.*;

public class HapiGetTopicInfo extends HapiQueryOp<HapiGetTopicInfo> {
	private static final Logger log = LogManager.getLogger(HapiGetTopicInfo.class);

	private final String topic;
	private Optional<String> topicMemo = Optional.empty();
	private OptionalLong seqNo = OptionalLong.empty();
	private Optional<LongSupplier> seqNoFn = Optional.empty();
	private Optional<byte[]> runningHash = Optional.empty();
	private OptionalLong expiry = OptionalLong.empty();
	private OptionalLong autoRenewPeriod = OptionalLong.empty();
	private boolean hasNoAdminKey = false;
	private Optional<String> adminKey = Optional.empty();
	private Optional<String> submitKey = Optional.empty();
	private Optional<String> autoRenewAccount = Optional.empty();

	public HapiGetTopicInfo(String topic) {
		this.topic = topic;
	}

	public HapiGetTopicInfo hasMemo(String memo)	{
		topicMemo = Optional.of(memo);
		return this;
	}

	public HapiGetTopicInfo hasSeqNo(long exp)	{
		seqNo = OptionalLong.of(exp);
		return this;
	}

	public HapiGetTopicInfo hasSeqNo(LongSupplier supplier)	{
		seqNoFn = Optional.of(supplier);
		return this;
	}

	public HapiGetTopicInfo hasRunningHash(byte[] exp)	{
		runningHash = Optional.of(exp);
		return this;
	}

	public HapiGetTopicInfo hasExpiry(long exp)	{
		expiry = OptionalLong.of(exp);
		return this;
	}
	public HapiGetTopicInfo hasAutoRenewPeriod(long exp)	{
		autoRenewPeriod = OptionalLong.of(exp);
		return this;
	}
	public HapiGetTopicInfo hasAdminKey(String exp) {
		adminKey = Optional.of(exp);
		return this;
	}
	public HapiGetTopicInfo hasNoAdminKey() {
		hasNoAdminKey = true;
		return this;
	}
	public HapiGetTopicInfo hasSubmitKey(String exp) {
		submitKey = Optional.of(exp);
		return this;
	}
	public HapiGetTopicInfo hasAutoRenewAccount(String exp) {
		autoRenewAccount = Optional.of(exp);
		return this;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.ConsensusGetTopicInfo;
	}

	@Override
	protected void submitWith(HapiApiSpec spec, Transaction payment) {
		Query query = getTopicInfoQuery(spec, payment, false);
		response = spec.clients().getConsSvcStub(targetNodeFor(spec), useTls).getTopicInfo(query);
		if (verboseLoggingOn) {
			log.info("Info: " + response.getConsensusGetTopicInfo().getTopicInfo());
		}
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) {
		ConsensusTopicInfo info = response.getConsensusGetTopicInfo().getTopicInfo();
		topicMemo.ifPresent(exp -> assertEquals("Bad memo!", exp, info.getMemo()));
		if (seqNoFn.isPresent()) {
			seqNo = OptionalLong.of(seqNoFn.get().getAsLong());
		}
		seqNo.ifPresent(exp -> assertEquals("Bad sequence number!", exp, info.getSequenceNumber()));
		runningHash.ifPresent(exp -> assertArrayEquals("Bad running hash!", exp,
				info.getRunningHash().toByteArray()));
		expiry.ifPresent(exp -> assertEquals("Bad expiry!", exp, info.getExpirationTime().getSeconds()));
		autoRenewPeriod.ifPresent(exp ->
				assertEquals("Bad auto-renew period!", exp, info.getAutoRenewPeriod().getSeconds()));
		adminKey.ifPresent(exp ->
				assertEquals("Bad admin key!", spec.registry().getKey(exp), info.getAdminKey()));
		submitKey.ifPresent(exp ->
				assertEquals("Bad submit key!", spec.registry().getKey(exp), info.getSubmitKey()));
		autoRenewAccount.ifPresent(exp ->
			assertEquals("Bad auto-renew account!", asId(exp, spec), info.getAutoRenewAccount()));
		if (hasNoAdminKey) {
			assertFalse("Should have no admin key!", info.hasAdminKey());
		}
	}

	@Override
	protected boolean needsPayment() {
		return true;
	}

	@Override
	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getTopicInfoQuery(spec, payment, true);
		Response response = spec.clients().getConsSvcStub(targetNodeFor(spec), useTls).getTopicInfo(query);
		return costFrom(response);
	}

	private Query getTopicInfoQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		ConsensusGetTopicInfoQuery topicGetInfo = ConsensusGetTopicInfoQuery.newBuilder()
				.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
				.setTopicID(TxnUtils.asTopicId(topic, spec))
				.build();
		return Query.newBuilder().setConsensusGetTopicInfo(topicGetInfo).build();
	}

	@Override
	protected HapiGetTopicInfo self() {
		return this;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		return super.toStringHelper().add("topic", topic);
	}
}
