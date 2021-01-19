package com.hedera.services.bdd.spec.queries.schedule;

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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoQuery;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.Optional;
import java.util.function.BiFunction;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;

public class HapiGetScheduleInfo extends HapiQueryOp<HapiGetScheduleInfo> {
    private static final Logger log = LogManager.getLogger(HapiGetScheduleInfo.class);

    String schedule;

    public HapiGetScheduleInfo(String schedule) {
        this.schedule = schedule;
    }

    Optional<String> expectedScheduleId = Optional.empty();
    Optional<String> expectedCreatorAccountID = Optional.empty();
    Optional<String> expectedPayerAccountID = Optional.empty();
    Optional<byte[]> expectedTransactionBody = Optional.empty();
    Optional<String> expectedAdminKey = Optional.empty();
    Optional<String[]> expectedSigners = Optional.empty();

    public HapiGetScheduleInfo hasScheduleId(String s) {
        expectedScheduleId = Optional.of(s);
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

    public HapiGetScheduleInfo hasTransactionBody(byte[] s) {
        expectedTransactionBody = Optional.of(s);
        return this;
    }

    public HapiGetScheduleInfo hasAdminKey(String s) {
        expectedAdminKey = Optional.of(s);
        return this;
    }

    public HapiGetScheduleInfo hasSigners(String[] s) {
        expectedSigners = Optional.of(s);
        return this;
    }

    @Override
    protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
        var actualInfo = response.getScheduleGetInfo().getScheduleInfo();

        if (expectedCreatorAccountID.isPresent()) {
            Assert.assertEquals(
                    "Wrong schedule creator account ID!",
                    expectedCreatorAccountID.get(),
                    actualInfo.getCreatorAccountID());
        }

        if (expectedPayerAccountID.isPresent()) {
            Assert.assertEquals(
                    "Wrong schedule payer account ID!",
                    expectedPayerAccountID.get(),
                    actualInfo.getPayerAccountID());
        }

        if (expectedTransactionBody.isPresent()) {
            Assert.assertEquals(
                    "Wrong schedule transaction body!",
                    expectedTransactionBody.get(),
                    actualInfo.getTransactionBody());
        }

        if (expectedSigners.isPresent()) {
            Assert.assertEquals(
                    "Wrong schedule signers!",
                    expectedSigners.get(),
                    actualInfo.getSigners());
        }

        if (expectedSigners.isPresent()) {
            var signers = expectedSigners.get();
            var actualSigners = actualInfo.getSigners().getKeysList();
            for (String s : signers) {
                Key potentialOne = Key.newBuilder().setECDSA384(ByteString.copyFrom(s.getBytes())).build();
                Key potentialTwo = Key.newBuilder().setEd25519(ByteString.copyFrom(s.getBytes())).build();
                Key potentialThree = Key.newBuilder().setRSA3072(ByteString.copyFrom(s.getBytes())).build();
                Assert.assertEquals(
                        "Wrong schedule signers!",
                        true,
                        actualSigners.contains(potentialOne) ||
                                actualSigners.contains(potentialTwo) ||
                                actualSigners.contains(potentialThree));
            }
        }

        var registry = spec.registry();
        assertFor(
                actualInfo.getScheduleID(),
                expectedScheduleId,
                (n, r) -> r.getScheduleID(n),
                "Wrong schedule id!",
                registry);

        assertFor(
                actualInfo.getAdminKey(),
                expectedAdminKey,
                (n, r) -> r.getAdminKey(schedule),
                "Wrong schedule admin key!",
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
