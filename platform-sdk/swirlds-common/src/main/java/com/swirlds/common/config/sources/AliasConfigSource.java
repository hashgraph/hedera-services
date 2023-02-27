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

package com.swirlds.common.config.sources;

import static com.swirlds.logging.LogMarker.CONFIG;

import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.source.ConfigSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link ConfigSource} that can be used a wrapper for other config sources and provides the functionality to define
 * aliases for given properties. By doing so the same property value that is defined for 1 name in the wrapped
 * {@link ConfigSource} can be accessed by multiple names.
 */
public class AliasConfigSource extends AbstractConfigSource {

    private static final Logger logger = LogManager.getLogger(AliasConfigSource.class);

    private final ConfigSource wrappedSource;

    private final Queue<Alias> aliases;

    /**
     * Constructor that takes the wrapped config.
     *
     * @param wrappedSource
     * 		the wrapped config
     */
    public AliasConfigSource(final ConfigSource wrappedSource) {
        this.wrappedSource = CommonUtils.throwArgNull(wrappedSource, "wrappedSource");
        this.aliases = new ConcurrentLinkedQueue<>();
    }

    /**
     * Adds the alias {@code 'alias'->'originalName'}
     *
     * @param alias
     * 		the alias name
     * @param originalName
     * 		the original name
     */
    public void addAlias(final String alias, final String originalName) {
        aliases.add(new Alias(alias, originalName));
    }

    private Map<String, String> properties;

    @Override
    protected Map<String, String> getInternalProperties() {
        if (properties == null) {
            final Map<String, String> internalProperties = wrappedSource.getProperties();
            final Map<String, String> aliasProperties = new HashMap<>();

            aliases.forEach(alias -> {
                if (internalProperties.containsKey(alias.alias)) {
                    logger.error(
                            CONFIG.getMarker(),
                            "Will not create alias {} since property '{}' already exits",
                            alias,
                            alias.alias);
                } else if (aliasProperties.containsKey(alias.alias)) {
                    logger.error(
                            CONFIG.getMarker(),
                            "Will not create alias {} since alias '{}' already exits",
                            alias,
                            alias.alias);
                } else if (!internalProperties.containsKey(alias.originalName)) {
                    logger.error(
                            CONFIG.getMarker(),
                            "Will not create alias {} since property '{}' does not exits",
                            alias,
                            alias.originalName);
                } else {
                    aliasProperties.put(alias.alias, internalProperties.get(alias.originalName));
                    logger.debug(CONFIG.getMarker(), "Added alias {}", alias);
                }
            });
            properties = new HashMap<>();
            properties.putAll(internalProperties);
            properties.putAll(aliasProperties);
        }
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public int getOrdinal() {
        return wrappedSource.getOrdinal();
    }

    private record Alias(String alias, String originalName) {

        private Alias {
            CommonUtils.throwArgBlank(alias, "alias");
            CommonUtils.throwArgBlank(originalName, "originalName");
            if (Objects.equals(originalName, alias)) {
                logger.warn(
                        CONFIG.getMarker(),
                        "originalName and alias are the same ({})! Will not create an alias",
                        alias);
            }
        }

        @Override
        public String toString() {
            return "'" + alias + "'->'" + originalName + "'";
        }
    }
}
