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

package com.hedera.services.bdd.junit.hedera.embedded;

/**
 * The modes in which an embedded network can be run.
 */
public enum EmbeddedMode {
    /**
     * Multiple specs can be run concurrently against the embedded network, and inherently nondeterministic
     * actions like thread scheduling and signing with ECDSA keys are supported.
     */
    CONCURRENT,
    /**
     * Only one spec can be run at a time against the embedded network, and all actions must be deterministic.
     */
    REPEATABLE,
}
