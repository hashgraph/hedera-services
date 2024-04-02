/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.streams.assertions.EventualRecordStreamAssertion.recordStreamLocFor;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.verification.traceability.ExpectedSidecar;
import com.hedera.services.bdd.spec.verification.traceability.SidecarWatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Order;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@SuppressWarnings("java:S5960") // "assertions should not be used in production code" - not production
public abstract class SidecarAwareHapiSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(SidecarAwareHapiSuite.class);

    private static SidecarWatcher sidecarWatcher;

    protected static void addExpectedSidecar(ExpectedSidecar expectedSidecar) {
        sidecarWatcher.addExpectedSidecar(expectedSidecar);
    }

    protected abstract List<HapiSpec> getSpecs();

    @Override
    public final List<HapiSpec> getSpecsInSuite() {
        Stream.Builder<HapiSpec> hapiSpecs = Stream.builder();
        hapiSpecs.add(sidecarWatcherSetup());

        for (HapiSpec hapiSpec : getSpecs()) {
            hapiSpecs.add(hapiSpec);
        }

        return hapiSpecs.add(assertSidecars())
                .build()
                .toList();
    }

    @HapiTest
    @Order(0)
    final HapiSpec sidecarWatcherSetup() {
        return defaultHapiSpec("initializeSidecarWatcher")
                .given()
                .when()
                .then(initializeSidecarWatcher());
    }

    @HapiTest
    @Order(Integer.MAX_VALUE)
    final HapiSpec assertSidecars() {
        return defaultHapiSpec("assertSidecars")
                .given(
                        // send a dummy transaction to trigger externalization of last sidecars
                        cryptoCreate("externalizeFinalSidecars").delayBy(2000))
                .when(tearDownSidecarWatcher())
                .then(assertNoMismatchedSidecars());
    }

    private static CustomSpecAssert initializeSidecarWatcher() {
        return withOpContext((spec, opLog) -> {
            Path path = Paths.get(recordStreamLocFor(spec));
            log.info("Watching for sidecars at absolute path {}", path.toAbsolutePath());
            sidecarWatcher = new SidecarWatcher(path);
            sidecarWatcher.watch();
        });
    }

    private static CustomSpecAssert tearDownSidecarWatcher() {
        return withOpContext((spec, opLog) -> {
            sidecarWatcher.waitUntilFinished();
            sidecarWatcher.tearDown();
        });
    }

    private static CustomSpecAssert assertNoMismatchedSidecars() {
        return assertionsHold((spec, assertLog) -> {
            assertTrue(sidecarWatcher.thereAreNoMismatchedSidecars(), sidecarWatcher.getMismatchErrors());
            assertTrue(
                    sidecarWatcher.thereAreNoPendingSidecars(),
                    "There are some sidecars that have not been yet"
                            + " externalized in the sidecar files after all"
                            + " specs: " + sidecarWatcher.getPendingErrors());
        });
    }
}
