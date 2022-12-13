/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.bdd.spec.queries.meta;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.NetworkGetExecutionTimeQuery;
import com.hederahashgraph.api.proto.java.NetworkGetExecutionTimeResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class HapiGetExecTime extends HapiQueryOp<HapiGetExecTime> {
    private static final Logger log = LogManager.getLogger(HapiGetExecTime.class);

    private List<TransactionID> txnIdsOfInterest;
    private NetworkGetExecutionTimeResponse timesResponse;

    private final List<String> txnsOfInterest;

    private long unsafeExecDuration;
    private TemporalUnit unsafeExecUnit = null;

    public HapiGetExecTime(List<String> txnsOfInterest) {
        this.txnsOfInterest = txnsOfInterest;
    }

    public HapiGetExecTime assertingNoneLongerThan(
            long unsafeExecDuration, TemporalUnit unsafeExecUnit) {
        this.unsafeExecUnit = unsafeExecUnit;
        this.unsafeExecDuration = unsafeExecDuration;
        return this;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.NetworkGetExecutionTime;
    }

    @Override
    protected HapiGetExecTime self() {
        return this;
    }

    @Override
    protected void assertExpectationsGiven(HapiSpec spec) throws Throwable {
        if (unsafeExecUnit != null) {
            final var maxDuration = Duration.of(unsafeExecDuration, unsafeExecUnit);
            final var nanosUsed = timesResponse.getExecutionTimesList();
            for (int i = 0, n = txnsOfInterest.size(); i < n; i++) {
                final var nanosUsedHere = nanosUsed.get(i);
                final var durationHere = Duration.ofNanos(nanosUsedHere);
                Assertions.assertTrue(
                        durationHere.compareTo(maxDuration) <= 0,
                        String.format(
                                "Transaction '%s' took %s, max allowed was %s!",
                                txnsOfInterest.get(i), durationHere, maxDuration));
            }
        }
    }

    @Override
    protected void submitWith(HapiSpec spec, Transaction payment) {
        Query query = getExecTimesQuery(spec, payment, false);
        response =
                spec.clients()
                        .getNetworkSvcStub(targetNodeFor(spec), useTls)
                        .getExecutionTime(query);
        timesResponse = response.getNetworkGetExecutionTime();
        if (verboseLoggingOn) {
            log.info("Exec times :: {}", asReadable(timesResponse.getExecutionTimesList()));
        }
    }

    private String asReadable(List<Long> execNanos) {
        assertEquals(txnIdsOfInterest.size(), execNanos.size());

        var sb = new StringBuilder("\n  ");
        boolean first = true;
        for (int i = 0, n = txnsOfInterest.size(); i < n; i++) {
            if (!first) {
                sb.append(",\n  ");
            }
            first = false;
            final var nanos = execNanos.get(i);
            sb.append(aligned(txnsOfInterest.get(i), 16, false))
                    .append(" took ")
                    .append(aligned("" + nanos, 12, true))
                    .append(" ns")
                    .append(" <-> ")
                    .append(aligned("" + (nanos / 1_000), 8, true))
                    .append(" µs");
        }
        return sb.toString();
    }

    private String aligned(String s, int len, boolean right) {
        if (s.length() > len) {
            final var suffixLen = len / 2 - 3;
            final var prefixLen = len - suffixLen - 3;
            s = s.substring(0, prefixLen) + "..." + s.substring(s.length() - suffixLen);
        }
        final var sb = new StringBuilder();
        int spacesNeeded = len - s.length();
        if (!right) {
            sb.append(s);
        }
        while (spacesNeeded-- > 0) {
            sb.append(" ");
        }
        if (right) {
            sb.append(s);
        }
        return sb.toString();
    }

    @Override
    protected long lookupCostWith(HapiSpec spec, Transaction payment) throws Throwable {
        Query query = getExecTimesQuery(spec, payment, true);
        Response response =
                spec.clients()
                        .getNetworkSvcStub(targetNodeFor(spec), useTls)
                        .getExecutionTime(query);
        return costFrom(response);
    }

    private Query getExecTimesQuery(HapiSpec spec, Transaction payment, boolean costOnly) {
        if (txnIdsOfInterest == null) {
            txnIdsOfInterest =
                    txnsOfInterest.stream()
                            .map(txn -> spec.registry().getTxnId(txn))
                            .collect(toList());
        }
        final var getExecTime =
                NetworkGetExecutionTimeQuery.newBuilder()
                        .setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
                        .addAllTransactionIds(txnIdsOfInterest)
                        .build();
        return Query.newBuilder().setNetworkGetExecutionTime(getExecTime).build();
    }

    @Override
    protected boolean needsPayment() {
        return true;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this);
    }
}
