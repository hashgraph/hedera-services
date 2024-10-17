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

package com.hedera.node.app.service.contract.impl.hevm;

import java.util.Map;

public enum HederaEvmVersion {
    /**
     * EVM version 0.30
     */
    VERSION_030("v0.30"),
    /**
     * EVM version 0.34
     */
    VERSION_034("v0.34"),
    /**
     * EVM version 0.38
     */
    VERSION_038("v0.38"),
    /**
     * EVM version 0.46
     */
    VERSION_046("v0.46"),
    /**
     * EVM version 0.50
     */
    VERSION_050("v0.50"), /* Cancun */
    /**
     * EVM version 0.51
     */
    VERSION_051("v0.51") /* Hedera Account Service System Contract */;

    /**
     * All supported EVM versions
     */
    public static final Map<String, HederaEvmVersion> EVM_VERSIONS = Map.of(
            VERSION_030.key(), VERSION_030,
            VERSION_034.key(), VERSION_034,
            VERSION_038.key(), VERSION_038,
            VERSION_046.key(), VERSION_046,
            VERSION_050.key(), VERSION_050,
            VERSION_051.key(), VERSION_051);

    HederaEvmVersion(String key) {
        this.key = key;
    }

    private final String key;

    public String key() {
        return key;
    }
}
