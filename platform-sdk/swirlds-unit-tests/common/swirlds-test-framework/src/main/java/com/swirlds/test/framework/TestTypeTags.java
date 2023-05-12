/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.test.framework;

/**
 * Contains tags for identifying the type of a test.
 */
public abstract class TestTypeTags {

    /**
     * Marks a test that verifies basic functional behavior.
     */
    public static final String FUNCTIONAL = "FUNCTIONAL";

    /**
     * Marks a test that performs integration operations.
     */
    public static final String INTEGRATION = "INTEGRATION";

    /**
     * Marks a test that measures performance.
     */
    public static final String PERFORMANCE = "PERFORMANCE";

    /**
     * Marks a test which asserts functionally correct behavior under stress/loads with many repeated iterations.
     */
    public static final String HAMMER = "HAMMER";

    /**
     * Marks a test which should only be executed manually with an attached debugger or profiler. These tests may
     * need to be manually stopped by a developer.
     */
    public static final String PROFILING_ONLY = "PROFILING_ONLY";

    /**
     * Marks a test which should not be executed on every PR or during normal test panel runs.
     */
    public static final String INFREQUENT_EXEC_ONLY = "INFREQUENT_EXEC_ONLY";
}
