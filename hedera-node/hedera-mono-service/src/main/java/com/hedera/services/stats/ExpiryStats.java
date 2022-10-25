/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stats;

import static com.hedera.services.stats.ServicesStatsManager.RUNNING_AVG_FORMAT;
import static com.hedera.services.stats.ServicesStatsManager.STAT_CATEGORY;

import com.google.common.annotations.VisibleForTesting;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.RunningAverageMetric;
import com.swirlds.common.system.Platform;
import javax.inject.Singleton;

@Singleton
public class ExpiryStats {
    private final double halfLife;
    private Counter contractsRemoved;
    private Counter contractsRenewed;
    private RunningAverageMetric idsScannedPerConsSec;

    public ExpiryStats(final double halfLife) {
        this.halfLife = halfLife;
    }

    public void registerWith(final Platform platform) {
        contractsRemoved =
                platform.getMetrics()
                        .getOrCreate(
                                new Counter.Config(
                                                STAT_CATEGORY,
                                                Names.CONTRACTS_REMOVED_SINCE_RESTART)
                                        .withDescription(
                                                Descriptions.CONTRACTS_REMOVED_SINCE_RESTART));
        contractsRenewed =
                platform.getMetrics()
                        .getOrCreate(
                                new Counter.Config(
                                                STAT_CATEGORY,
                                                Names.CONTRACTS_RENEWED_SINCE_RESTART)
                                        .withDescription(
                                                Descriptions.CONTRACTS_RENEWED_SINCE_RESTART));
        idsScannedPerConsSec =
                platform.getMetrics()
                        .getOrCreate(
                                new RunningAverageMetric.Config(
                                                STAT_CATEGORY, Names.IDS_SCANNED_PER_CONSENSUS_SEC)
                                        .withDescription(Descriptions.IDS_SCANNED_PER_CONSENSUS_SEC)
                                        .withFormat(RUNNING_AVG_FORMAT)
                                        .withHalfLife(halfLife));
    }

    public void countRemovedContract() {
        contractsRemoved.increment();
    }

    public void countRenewedContract() {
        contractsRenewed.increment();
    }

    public void includeIdsScannedInLastConsSec(final long n) {
        idsScannedPerConsSec.update(n);
    }

    public static final class Descriptions {
        static final String IDS_SCANNED_PER_CONSENSUS_SEC =
                "average entity ids scanned per second of consensus time";
        static final String CONTRACTS_REMOVED_SINCE_RESTART =
                "number of expired contracts removed since last restart";
        static final String CONTRACTS_RENEWED_SINCE_RESTART =
                "number of expired contracts renewed since last restart";

        private Descriptions() {
            throw new UnsupportedOperationException("Utility Class");
        }
    }

    public static final class Names {
        static final String IDS_SCANNED_PER_CONSENSUS_SEC = "idsScannedPerConsSec";
        static final String CONTRACTS_REMOVED_SINCE_RESTART = "contractsRemoved";
        static final String CONTRACTS_RENEWED_SINCE_RESTART = "contractsRenewed";

        private Names() {
            throw new UnsupportedOperationException("Utility Class");
        }
    }

    @VisibleForTesting
    void setContractsRemoved(final Counter contractsRemoved) {
        this.contractsRemoved = contractsRemoved;
    }

    @VisibleForTesting
    void setContractsRenewed(final Counter contractsRenewed) {
        this.contractsRenewed = contractsRenewed;
    }

    @VisibleForTesting
    void setIdsScannedPerConsSec(final RunningAverageMetric idsScannedPerConsSec) {
        this.idsScannedPerConsSec = idsScannedPerConsSec;
    }
}
