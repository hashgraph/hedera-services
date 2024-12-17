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

package com.hedera.services.bdd.junit.hedera;

/**
 * Enumerates files and directories created and used by a Hedera node.
 */
public enum ExternalPath {
    APPLICATION_LOG,
    SWIRLDS_LOG,
    ADDRESS_BOOK,
    GENESIS_PROPERTIES,
    APPLICATION_PROPERTIES,
    NODE_ADMIN_KEYS_JSON,
    LOG4J2_XML,
    RECORD_STREAMS_DIR,
    BLOCK_STREAMS_DIR,
    DATA_CONFIG_DIR,
    UPGRADE_ARTIFACTS_DIR,
    SAVED_STATES_DIR,
}
