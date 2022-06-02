package com.hedera.services.bdd.spec.queries.schedule;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoQuery;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate.correspondingScheduledTxnId;
import static com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate.getRelativeExpiry;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;

public class HapiGetScheduleInfo extends HapiQueryOp<HapiGetScheduleInfo> {
	private static final Logger log = LogManager.getLogger(HapiGetScheduleInfo.class);

	String schedule;

	public HapiGetScheduleInfo(String schedule) {
		this.schedule = schedule;
	}

	boolean shouldBeExecuted = false;
	boolean shouldNotBeExecuted = false;
	boolean shouldNotBeDeleted = false;
	boolean checkForRecordedScheduledTxn = false;
	Optional<String> deletionTxn = Optional.empty();
	Optional<String> executionTxn = Optional.empty();
	Optional<String> expectedScheduleId = Optional.empty();
	Optional<Boolean> expectedWaitForExpiry = Optional.empty();
	Optional<Pair<String, Long>> expectedExpirationTimeRelativeTo = Optional.empty();
	Optional<String> expectedCreatorAccountID = Optional.empty();
	Optional<String> expectedPayerAccountID = Optional.empty();
	Optional<String> expectedScheduledTxnId = Optional.empty();
	Optional<String> expectedAdminKey = Optional.empty();
	Optional<String> expectedEntityMemo = Optional.empty();
	Optional<List<String>> expectedSignatories = Optional.empty();

	public HapiGetScheduleInfo hasScheduledTxnIdSavedBy(String creation) {
		expectedScheduledTxnId = Optional.of(creation);
		return this;
	}

	public HapiGetScheduleInfo isExecuted() {
		shouldBeExecuted = true;
		return this;
	}

	public HapiGetScheduleInfo isNotExecuted() {
		shouldNotBeExecuted = true;
		return this;
	}

	public HapiGetScheduleInfo isNotDeleted() {
		shouldNotBeDeleted = true;
		return this;
	}

	public HapiGetScheduleInfo wasDeletedAtConsensusTimeOf(String txn) {
		deletionTxn = Optional.of(txn);
		return this;
	}

	public HapiGetScheduleInfo wasExecutedBy(String txn) {
		executionTxn = Optional.of(txn);
		return this;
	}

	public HapiGetScheduleInfo hasScheduleId(String s) {
		expectedScheduleId = Optional.of(s);
		return this;
	}

	public HapiGetScheduleInfo hasWaitForExpiry() {
		expectedWaitForExpiry = Optional.of(true);
		return this;
	}

	public HapiGetScheduleInfo hasWaitForExpiry(boolean value) {
		expectedWaitForExpiry = Optional.of(value);
		return this;
	}

	public HapiGetScheduleInfo hasRelativeExpiry(String txnId, long offsetSeconds) {
		this.expectedExpirationTimeRelativeTo = Optional.of(Pair.of(txnId, offsetSeconds));
		return this;
	}

	public HapiGetScheduleInfo hasCreatorAccountID(String s) {
		expectedCreatorAccountID = Optional.of(s);
		return this;
	}

	public HapiGetScheduleInfo hasPayerAccountID(String s) {
		expectedPayerAccountID = Optional.of(s);
		return this;
	}

	public HapiGetScheduleInfo hasRecordedScheduledTxn() {
		checkForRecordedScheduledTxn = true;
		return this;
	}

	public HapiGetScheduleInfo hasAdminKey(String s) {
		expectedAdminKey = Optional.of(s);
		return this;
	}

	public HapiGetScheduleInfo hasEntityMemo(String s) {
		expectedEntityMemo = Optional.of(s);
		return this;
	}

	public HapiGetScheduleInfo hasSignatories(String... s) {
		expectedSignatories = Optional.of(List.of(s));
		return this;
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) {
		var actualInfo = response.getScheduleGetInfo().getScheduleInfo();

		expectedScheduledTxnId.ifPresent(n -> Assertions.assertEquals(
				spec.registry().getTxnId(correspondingScheduledTxnId(n)),
				actualInfo.getScheduledTransactionID(),
				"Wrong scheduled transaction id!"));

		expectedCreatorAccountID.ifPresent(s -> Assertions.assertEquals(
				TxnUtils.asId(s, spec),
				actualInfo.getCreatorAccountID(),
				"Wrong schedule creator account ID!"));

		expectedPayerAccountID.ifPresent(s -> Assertions.assertEquals(
				TxnUtils.asId(s, spec),
				actualInfo.getPayerAccountID(),
				"Wrong schedule payer account ID!"));

		expectedEntityMemo.ifPresent(s -> Assertions.assertEquals(
				s,
				actualInfo.getMemo(),
				"Wrong memo!"));

		if (checkForRecordedScheduledTxn) {
			Assertions.assertEquals(
					spec.registry().getScheduledTxn(schedule),
					actualInfo.getScheduledTransactionBody(),
					"Wrong scheduled txn!");
		}

		if (shouldBeExecuted) {
			Assertions.assertTrue(actualInfo.hasExecutionTime(), "Wasn't already executed!");
		}

		if (shouldNotBeExecuted) {
			Assertions.assertFalse(actualInfo.hasExecutionTime(), "Was already executed!");
		}

		if (shouldNotBeDeleted) {
			Assertions.assertFalse(actualInfo.hasDeletionTime(), "Was already deleted!");
		}

		if (deletionTxn.isPresent()) {
			assertTimestampMatches(
					deletionTxn.get(),
					0,
					actualInfo.getDeletionTime(),
					"Wrong consensus deletion time!",
					spec);
		}

		var registry = spec.registry();

		expectedSignatories.ifPresent(s -> {
			var expect = KeyList.newBuilder();
			for (String signatory : s) {
				var key = registry.getKey(signatory);
				expect.addKeys(key);
			}
			Assertions.assertArrayEquals(
					expect.build().getKeysList().toArray(),
					actualInfo.getSigners().getKeysList().toArray(),
					"Wrong signatories!");
		});

		expectedExpirationTimeRelativeTo.ifPresent(stringLongPair ->
				Assertions.assertEquals(
						getRelativeExpiry(spec,
								stringLongPair.getKey(),
								stringLongPair.getValue()),
						actualInfo.getExpirationTime(),
						"Wrong Expiration Time!"
				)
		);

		expectedWaitForExpiry.ifPresent(aBoolean ->
				Assertions.assertEquals(aBoolean, actualInfo.getWaitForExpiry(), "waitForExpiry was wrong!"));

		assertFor(
				actualInfo.getAdminKey(),
				expectedAdminKey,
				(n, r) -> r.getAdminKey(schedule),
				"Wrong schedule admin key!",
				registry);

		expectedLedgerId.ifPresent(id -> Assertions.assertEquals(rationalize(id), actualInfo.getLedgerId()));
	}

	private void assertTimestampMatches(
			String txn,
			int nanoOffset,
			Timestamp actual,
			String errMsg,
			HapiApiSpec spec
	) {
		var subOp = getTxnRecord(txn);
		allRunFor(spec, subOp);
		var consensusTime = subOp.getResponseRecord().getConsensusTimestamp();
		var expected = Timestamp.newBuilder()
				.setSeconds(actual.getSeconds())
				.setNanos(consensusTime.getNanos() + nanoOffset)
				.build();
		Assertions.assertEquals(expected, actual, errMsg);
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
	protected void submitWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getScheduleInfoQuery(spec, payment, false);
		response = spec.clients().getScheduleSvcStub(targetNodeFor(spec), useTls).getScheduleInfo(query);
		if (verboseLoggingOn) {
			log.info("Info for '" + schedule + "': " + response.getScheduleGetInfo().getScheduleInfo());
		}
	}

	@Override
	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getScheduleInfoQuery(spec, payment, true);
		Response response = spec.clients().getScheduleSvcStub(targetNodeFor(spec), useTls).getScheduleInfo(query);
		return costFrom(response);
	}

	private Query getScheduleInfoQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		var id = TxnUtils.asScheduleId(schedule, spec);
		ScheduleGetInfoQuery getScheduleQuery = ScheduleGetInfoQuery.newBuilder()
				.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
				.setScheduleID(id)
				.build();
		return Query.newBuilder().setScheduleGetInfo(getScheduleQuery).build();
	}

	@Override
	protected boolean needsPayment() {
		return true;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.ScheduleGetInfo;
	}

	@Override
	protected HapiGetScheduleInfo self() {
		return this;
	}
}
