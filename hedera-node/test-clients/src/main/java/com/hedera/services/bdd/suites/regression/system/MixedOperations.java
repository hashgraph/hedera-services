/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.regression.system;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.suites.regression.factories.RegressionProviderFactory.factoryFrom;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.regression.UmbrellaRedux;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Returns mixed operations that can be used for restart and reconnect tests.
 * These operations will be further extended in the future
 */
public class MixedOperations {
    final int numSubmissions;

    public MixedOperations(int numSubmissions) {
        this.numSubmissions = numSubmissions;
    }

    public static HapiSpecOperation burstOfTps(final int tps, @NonNull final Duration duration) {
        return runWithProvider(factoryFrom(() -> UmbrellaRedux.DEFAULT_PROPERTIES))
                .lasting(duration.toMillis(), TimeUnit.MILLISECONDS)
                .maxOpsPerSec(tps)
                .loggingOff();
    }
}
