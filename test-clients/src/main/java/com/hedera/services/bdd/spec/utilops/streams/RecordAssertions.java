/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.utilops.streams;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;

import com.hedera.services.bdd.junit.RecordStreamValidator;
import com.hedera.services.bdd.junit.TestBase;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RecordAssertions extends UtilOp {
    private static final Duration DEFAULT_RECORD_CLOSE_DELAY = Duration.ofMillis(100L);
    private static final Duration DEFAULT_INTER_CHECK_DELAY = Duration.ofMillis(2_000L);

    @Nullable private final String loc;
    private final Duration timeout;
    private final List<RecordStreamValidator> validators;

    public RecordAssertions(final Duration timeout, final RecordStreamValidator... validators) {
        this(null, timeout, validators);
    }

    public RecordAssertions(
            final String loc, final Duration timeout, final RecordStreamValidator... validators) {
        this.loc = loc;
        this.timeout = timeout;
        this.validators = Arrays.asList(validators);
    }

    @Override
    protected boolean submitOp(final HapiSpec spec) throws Throwable {
        final var locToUse = loc == null ? spec.setup().defaultRecordLoc() : loc;
        final var deadline = Instant.now().plus(timeout);
        Throwable lastFailure;
        do {
            triggerAndCloseAtLeastOneFile(spec);
            lastFailure = firstFailureIfAny(locToUse);
            if (lastFailure == null) {
                // No failure, so we're done!
                return false;
            }
        } while (Instant.now().isBefore(deadline));
        throw Objects.requireNonNull(lastFailure);
    }

    public static void triggerAndCloseAtLeastOneFile(final HapiSpec spec)
            throws InterruptedException {
        Thread.sleep(DEFAULT_INTER_CHECK_DELAY.toMillis());
        // Should trigger a new record to be written if we have crossed a 2-second boundary
        final var triggerOp =
                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L))
                        .deferStatusResolution()
                        .hasAnyStatusAtAll()
                        .noLogging();
        allRunFor(spec, triggerOp);
        Thread.sleep(DEFAULT_RECORD_CLOSE_DELAY.toMillis());
    }

    @Nullable
    private Throwable firstFailureIfAny(final String loc) {
        try {
            TestBase.assertValidatorsPass(loc, validators);
            return null;
        } catch (final Throwable t) {
            return t;
        }
    }
}
