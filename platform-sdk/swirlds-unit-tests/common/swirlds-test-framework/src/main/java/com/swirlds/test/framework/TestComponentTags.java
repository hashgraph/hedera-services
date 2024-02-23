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
 * Tags that denote the component that a test is verifying.
 */
public abstract class TestComponentTags {

    public static final String CONSENSUS = "CONSENSUS";

    public static final String MMAP = "MMAP";

    public static final String VMAP = "VMAP";

    public static final String FCQUEUE = "FCQUEUE";

    public static final String GOSSIP = "GOSSIP";

    public static final String IO = "IO";

    public static final String LOGGING = "LOGGING";

    public static final String MERKLE = "MERKLE";

    public static final String MERKLETREE = "MERKLETREE";

    public static final String PLATFORM = "PLATFORM";

    public static final String EXPECTED_MAP = "EXPECTED_MAP";

    public static final String VALIDATOR = "VALIDATOR";

    public static final String RECONNECT = "RECONNECT";

    public static final String TESTING = "TESTING";

    public static final String THREADING = "THREADING";

    public static final String NOTIFICATION = "NOTIFICATION";
}
