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

package com.swirlds.merkledb.settings;

/**
 * Static holder of resolved {@code merkleDb.*} settings, via an instance of {@link MerkleDbSettings}.
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public final class MerkleDbSettingsFactory {

    private static MerkleDbSettings merkleDbSettings;

    /**
     * Hook that {@code Browser#populateSettingsCommon()} will use to attach the instance of
     * {@link MerkleDbSettings} obtained by parsing <i>settings.txt</i>.
     */
    public static void configure(final MerkleDbSettings merkleDbSettings) {
        MerkleDbSettingsFactory.merkleDbSettings = merkleDbSettings;
    }

    /**
     * Get the configured settings for MerkleDB.
     */
    public static MerkleDbSettings get() {
        if (merkleDbSettings == null) {
            merkleDbSettings = new DefaultMerkleDbSettings();
        }
        return merkleDbSettings;
    }

    private MerkleDbSettingsFactory() {
        /* Utility class */
    }
}
