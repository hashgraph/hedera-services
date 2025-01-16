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

import com.hedera.node.config.data.BootstrapConfig;

/**
 * Enumerates sources of admin keys for a Hedera node. Arrays containing none, one, or any number of
 * these enums can be passed to various embedded test framework methods in order to determine if these
 * files should be present for the node bootstrapping process. If multiple key sources are present at
 * startup, the framework implementation should honor the following order. In other words, <b>these
 * values are listed in expected order of precedence.</b>
 */
public enum AdminKeySource {
    /**
     * Represents a genesis-network.json file
     */
    GENESIS_NETWORK_FILE,

    /**
     * Represents a node-admin-keys.json file, loaded from {@link BootstrapConfig} via its
     * {@code nodeAdminKeysPath} property
     */
    NODE_ADMIN_KEYS_FILE,

    /**
     * Represents a PEM file containing the admin key, loaded from {@link BootstrapConfig} via its
     * {@code pemAdminKeyPath} property.
     * <p>
     *     <b>NOTE:</b> This key MUST only be used for testing purposes; it should never be used in
     *     a production environment.
     */
    PEM_FILE;

    public static final AdminKeySource[] DEFAULTS = new AdminKeySource[] {GENESIS_NETWORK_FILE, NODE_ADMIN_KEYS_FILE};
}
