/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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
 * Tags that describe various features of a test's behavior or the way that a test should be run.
 */
public abstract class TestQualifierTags {

    /**
     * Denotes a test that runs at scale, e.g. inserts a million things into the database.
     *
     * Most tests that run longer than a couple of seconds should probably carry this label.
     */
    public static final String AT_SCALE = "AT_SCALE";

    /**
     * Denotes a broken tests. This test is expected to fail or be flaky but should not be removed from the codebase.
     */
    public static final String BROKEN = "BROKEN";

    /**
     * Denotes a test that requires more resources than a laptop is likely to have.
     */
    public static final String REMOTE_ONLY = "REMOTE_ONLY";

    /**
     * Denotes a test that verifies minimum acceptable functionality and must pass on every commit
     */
    public static final String MIN_ACCEPTED_TEST = "MIN_ACCEPTED_TEST";

    /**
     * Denotes a test that normally needs more than 100 ms to be executed
     */
    public static final String TIME_CONSUMING = "TIME_CONSUMING";
}
