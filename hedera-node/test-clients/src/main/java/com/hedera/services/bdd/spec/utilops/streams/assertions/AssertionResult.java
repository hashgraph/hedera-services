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

package com.hedera.services.bdd.spec.utilops.streams.assertions;

import com.hedera.services.bdd.spec.utilops.streams.AssertionOutcome;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;

public class AssertionResult {
    private static final AssertionResult SUCCESS = new AssertionResult(null, AssertionOutcome.SUCCESS);

    @Nullable
    private final String errorDetails;

    private final AssertionOutcome outcome;

    public AssertionResult(final @Nullable String errorDetails, final AssertionOutcome outcome) {
        this.errorDetails = errorDetails;
        this.outcome = outcome;
    }

    public static AssertionResult success() {
        return SUCCESS;
    }

    public static AssertionResult timeout(final Duration timeout) {
        return new AssertionResult("Timed out in " + timeout, AssertionOutcome.TIMEOUT);
    }

    public static AssertionResult failure(final String failureDetails) {
        return new AssertionResult(failureDetails, AssertionOutcome.FAILURE);
    }

    public boolean passed() {
        return outcome == AssertionOutcome.SUCCESS;
    }

    @Nullable
    public String getErrorDetails() {
        return errorDetails;
    }
}
