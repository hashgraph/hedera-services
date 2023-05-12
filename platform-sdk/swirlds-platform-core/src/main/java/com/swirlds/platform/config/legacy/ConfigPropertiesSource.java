/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.config.legacy;

import static com.swirlds.common.config.sources.ConfigSourceOrdinalConstants.LEGACY_PROPERTY_FILE_ORDINAL_FOR_CONFIG;

import com.swirlds.config.api.source.ConfigSource;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;

/**
 * This class provides all properties from {@link LegacyConfigProperties} (the config.txt file) to the new config API. In
 * the old implementation some values from the config.txt have overwritten settings. By this {@link ConfigSource} this
 * behavior is recreated for the new config api.
 *
 * @deprecated Once we migrated the config.txt to the new config api and property files the class can be removed.
 */
@Deprecated(forRemoval = true)
public class ConfigPropertiesSource implements ConfigSource {

    private final Properties properties;

    public ConfigPropertiesSource(final LegacyConfigProperties configurationProperties) {
        this.properties = new Properties();
        configurationProperties.tls().ifPresent(value -> properties.setProperty("useTLS", Boolean.toString(value)));
        configurationProperties
                .maxSyncs()
                .ifPresent(value -> properties.setProperty("maxOutgoingSyncs", Integer.toString(value)));
        configurationProperties
                .transactionMaxBytes()
                .ifPresent(value -> properties.setProperty("transactionMaxBytes", Integer.toString(value)));
        configurationProperties
                .ipTos()
                .ifPresent(value -> properties.setProperty("socketIpTos", Integer.toString(value)));
        configurationProperties
                .saveStatePeriod()
                .ifPresent(value -> properties.setProperty("state.saveStatePeriod", Integer.toString(value)));
        configurationProperties
                .genesisFreezeTime()
                .ifPresent(value -> properties.setProperty("genesisFreezeTime", Long.toString(value)));
    }

    @Override
    public int getOrdinal() {
        return LEGACY_PROPERTY_FILE_ORDINAL_FOR_CONFIG;
    }

    @Override
    public Set<String> getPropertyNames() {
        return properties.stringPropertyNames();
    }

    @Override
    public String getValue(final String propertyName) throws NoSuchElementException {
        return properties.getProperty(propertyName);
    }
}
