/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.stats;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.math.Stats;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class HapiStats {
    private final boolean hasConsensusLatencies;
    private final List<OpObs> obs;

    public HapiStats(boolean hasConsensusLatencies, List<OpObs> obs) {
        this.hasConsensusLatencies = hasConsensusLatencies;
        this.obs = obs;
    }

    public Stats queryResponseLatencyStats() {
        return Stats.of(queryObs().stream().map(QueryObs::getResponseLatency).collect(toList()));
    }

    public Stats txnResponseLatencyStats() {
        return Stats.of(txnObs().stream().map(TxnObs::getResponseLatency).collect(toList()));
    }

    public Stats txnResponseLatencyStatsFor(HederaFunctionality txnType) {
        return Stats.of(
                txnObs().stream()
                        .filter(obs -> obs.functionality().equals(txnType))
                        .map(TxnObs::getResponseLatency)
                        .collect(toList()));
    }

    public <T extends OpObs> Stats statsProjectedFor(
            Class<T> tClass, Predicate<T> relevancy, Function<T, Long> projection) {
        return Stats.of(
                (tClass.equals(TxnObs.class) ? txnObs() : queryObs())
                        .stream()
                                .filter(obs -> relevancy.test(tClass.cast(obs)))
                                .map(obs -> projection.apply(tClass.cast(obs)))
                                .collect(toList()));
    }

    public Map<HederaFunctionality, Long> countDetails() {
        return obs.stream().collect(groupingBy(OpObs::functionality, counting()));
    }

    public List<TxnObs> txnObs() {
        return obs.stream()
                .filter(op -> op instanceof TxnObs)
                .map(op -> (TxnObs) op)
                .collect(toList());
    }

    public Set<HederaFunctionality> queryTypes() {
        return queryObs().stream().map(QueryObs::functionality).collect(toSet());
    }

    public Set<HederaFunctionality> txnTypes() {
        return txnObs().stream().map(TxnObs::functionality).collect(toSet());
    }

    public List<TxnObs> acceptedTxnObs() {
        return txnObs().stream().filter(TxnObs::wasAccepted).collect(toList());
    }

    public List<QueryObs> queryObs() {
        return obs.stream()
                .filter(op -> op instanceof QueryObs)
                .map(op -> (QueryObs) op)
                .collect(toList());
    }

    public Stats txnResponseLatencyStatsFor() {
        assertLatencyWasMeasured();
        return Stats.of(
                acceptedTxnObs().stream().map(TxnObs::getConsensusLatency).collect(toList()));
    }

    private void assertLatencyWasMeasured() {
        if (!hasConsensusLatencies) {
            throw new IllegalStateException("Consensus latencies were not measured!");
        }
    }

    public int numTxns() {
        return txnObs().size();
    }

    public int numQueries() {
        return queryObs().size();
    }

    public int numOps() {
        return obs.size();
    }
}
