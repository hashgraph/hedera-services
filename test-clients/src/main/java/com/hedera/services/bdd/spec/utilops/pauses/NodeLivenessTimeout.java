/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.utilops.pauses;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NodeLivenessTimeout extends UtilOp {
    static final Logger log = LogManager.getLogger(NodeLivenessTimeout.class);

    private int duration = 30;
    private final String node;
    private TimeUnit unit = TimeUnit.SECONDS;
    private int logIntervalDuration = 1;
    private int retryIntervalDuration = 1;

    public NodeLivenessTimeout(String node) {
        this.node = node;
    }

    public NodeLivenessTimeout within(int duration, TimeUnit unit) {
        this.unit = unit;
        this.duration = duration;
        return this;
    }

    public NodeLivenessTimeout loggingAvailabilityEvery(int duration) {
        logIntervalDuration = duration;
        return this;
    }

    public NodeLivenessTimeout sleepingBetweenRetriesFor(int duration) {
        retryIntervalDuration = duration;
        return this;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        log.info("Requiring node {} to be available in {} {}", node, duration, unit);

        int numSleeps = 0;
        var now = Instant.now();
        var deadline = now.plus(duration, unit.toChronoUnit());
        var nextDeadLog = now.plus(logIntervalDuration, unit.toChronoUnit());
        while (Instant.now().isBefore(deadline)) {
            var op = getAccountInfo("0.0.2").setNode(node);
            var error = op.execFor(spec);
            if (error.isEmpty()) {
                log.info(
                        " --> Node {} available after {} {}",
                        node,
                        numSleeps * retryIntervalDuration,
                        unit);
                return false;
            }
            unit.sleep(retryIntervalDuration);
            numSleeps++;
            if ((now = Instant.now()).isAfter(nextDeadLog)) {
                log.info(
                        " --> Node {} not available after {} {} response {}",
                        node,
                        numSleeps * retryIntervalDuration,
                        unit,
                        error.get());
                nextDeadLog = now.plus(logIntervalDuration, unit.toChronoUnit());
            }
        }

        throw new AssertionError(
                String.format("Node %s wasn't available in %d %s!", node, duration, unit));
    }
}
