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

package com.swirlds.platform.testreader;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A row in a JRS Test Report.
 *
 * @param tests    the results of the tests in the row, ordered from most to least recent
 * @param metadata test metadata that is not parsed from results
 */
public record JrsTestReportRow(@NonNull List<JrsTestResult> tests, @Nullable JrsTestMetadata metadata) {

    /**
     * Get the most recent test in this row.
     */
    @NonNull
    public JrsTestResult getMostRecentTest() {
        return tests.get(0);
    }
}
