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

package com.swirlds.common.test.fixtures.junit.tags;

public abstract class TestQualifierTags {

    /**
     * Denotes a test that normally needs more than 100 ms to be executed
     * This tag will be removed, and instead all annotated tests are moved to a separate 'src/timeConsuming' source set.
     */
    public static final String TIME_CONSUMING = "TIME_CONSUMING";

    /**
     * Denotes that a test is so resource sensitive (e.g. uses Thread.sleep()) that the test task running the test
     * needs to run without anything in parallel.
     * Tests in this category should be fixed to not being flaky, or moved to the 'hammer' category (that is also
     * running in isolation) if they fit that category.
     */
    public static final String TIMING_SENSITIVE = "TIMING_SENSITIVE";
}
