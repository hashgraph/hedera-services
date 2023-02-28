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

package com.hedera.services.bdd.spec.utilops.throughput;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.stats.ThroughputObs;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FinishThroughputObs extends UtilOp {
    private static final Logger LOG = LogManager.getLogger(FinishThroughputObs.class);

    private final String name;
    Optional<Long> sleepMs = Optional.empty();
    Optional<Long> maxObsLengthMs = Optional.empty();
    Optional<Supplier<HapiQueryOp<?>[]>> gateSupplier = Optional.empty();

    public FinishThroughputObs(String name) {
        this.name = name;
    }

    public FinishThroughputObs gatedByQueries(Supplier<HapiQueryOp<?>[]> queriesSupplier) {
        gateSupplier = Optional.of(queriesSupplier);
        return this;
    }

    public FinishThroughputObs gatedByQuery(Supplier<HapiQueryOp<?>> querySupplier) {
        gateSupplier = Optional.of(() -> new HapiQueryOp<?>[] {querySupplier.get()});
        return this;
    }

    public FinishThroughputObs sleepMs(long length) {
        sleepMs = Optional.of(length);
        return this;
    }

    public FinishThroughputObs expiryMs(long length) {
        maxObsLengthMs = Optional.of(length);
        return this;
    }

    @Override
    @SuppressWarnings("java:S3516")
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        ThroughputObs baseObs = spec.registry().getThroughputObs(name);

        if (gateSupplier.isEmpty()) {
            int n = spec.numLedgerOps() - baseObs.getNumOpsAtObservationStart();
            long timeToOpenGate = System.currentTimeMillis() - baseObs.getCreationTime();
            LOG.info("{}{} saw {} ops for its throughput measurement.", spec::logPrefix, () -> this, () -> n);
            baseObs.setNumOpsAtObservationFinish(spec.numLedgerOps());
            baseObs.setObsLengthMs(timeToOpenGate);
            return false;
        }

        sleepUntil(baseObs.getExpectedQueueSaturationTime());
        if (baseObs.getNumOpsAtExpectedQueueSaturation() == -1) {
            LOG.warn("{}{} saw no ops executed after the expected queue saturation time!", spec::logPrefix, () -> this);
            return false;
        }
        LOG.info(
                "{}{} {} ops had been executed at queue saturation time.",
                spec::logPrefix,
                () -> this,
                baseObs::getNumOpsAtExpectedQueueSaturation);

        long now = System.currentTimeMillis();
        long sleepTimeMs = sleepMs.orElse(spec.setup().defaultThroughputObsSleepMs());
        long obsExpirationTime = now + maxObsLengthMs.orElse(spec.setup().defaultThroughputObsExpiryMs());
        HapiQueryOp<?>[] gatingQueries = gateSupplier.get().get();
        while (gatingQueries.length > 0 && now < obsExpirationTime) {
            try {
                CustomSpecAssert.allRunFor(spec, gatingQueries);
                break;
            } catch (final Exception ignored) {
                // Intentionally ignored
            }
            LOG.info("{}{} sleeping {}ms before retrying gate!", spec::logPrefix, () -> this, () -> sleepTimeMs);
            try {
                MILLISECONDS.sleep(sleepTimeMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            now = System.currentTimeMillis();
            gatingQueries = gateSupplier.get().get();
        }

        if (now < obsExpirationTime) {
            int n = spec.numLedgerOps() - baseObs.getNumOpsAtExpectedQueueSaturation();
            long timeToOpenGate = now - baseObs.getExpectedQueueSaturationTime();
            LOG.info(
                    "{}{} observed {} ops before gating queries passed in {}ms.",
                    spec::logPrefix,
                    () -> this,
                    () -> n,
                    () -> timeToOpenGate);
            baseObs.setNumOpsAtObservationFinish(spec.numLedgerOps());
            baseObs.setObsLengthMs(timeToOpenGate);
        } else {
            LOG.warn("{}{} never saw its gating queries pass!", spec::logPrefix, () -> this);
        }

        return false;
    }

    private void sleepUntil(long t) {
        long now = System.currentTimeMillis();
        while (now < t) {
            try {
                Thread.sleep(t - now + 1L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            now = System.currentTimeMillis();
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("finishing", name).toString();
    }
}
