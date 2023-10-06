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

package com.swirlds.common.config.sources;

import com.swirlds.config.api.source.ConfigSource;

/**
 * Class that provides constant values for ordinals (see {@link ConfigSource#getOrdinal()}).
 */
public final class ConfigSourceOrdinalConstants {
    /**
     * Ordinal for system properties.
     */
    static final int SYSTEM_PROPERTIES_ORDINAL = 400;
    /**
     * Ordinal for system environment.
     */
    static final int SYSTEM_ENVIRONMENT_ORDINAL = 300;
    /**
     * Ordinal for property files.
     */
    static final int PROPERTY_FILE_ORDINAL = 200;
    /**
     * Ordinal for property files with the old syntax of swirlds settings.
     *
     * @deprecated should be removed once the old file format is not used anymore
     */
    @Deprecated(forRemoval = true)
    static final int LEGACY_PROPERTY_FILE_ORDINAL = 100;

    @Deprecated(forRemoval = true)
    static final int LEGACY_PROPERTY_FILE_ORDINAL_FOR_SETTINGS = LEGACY_PROPERTY_FILE_ORDINAL + 10;

    /**
     * Ordinal for default values.
     */
    static final int PROGRAMMATIC_VALUES_ORDINAL = 10;

    private ConfigSourceOrdinalConstants() {}
}
