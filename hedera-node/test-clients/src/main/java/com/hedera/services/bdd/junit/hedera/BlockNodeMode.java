/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.hedera;

/**
 * Defines the different modes for block node operation in tests.
 */
public enum BlockNodeMode {
    /** Use Docker containers for block nodes */
    CONTAINERS,

    /** Use a simulated block node */
    SIMULATOR,

    /** User is already running a local hedera block node. SubProcessNode 1 will connect to it. */
    LOCAL_NODE,

    /** Don't use any block nodes */
    NONE
}
