/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.version;

import com.hedera.node.config.converter.SemanticVersionConverter;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.node.config.sources.PropertyConfigSource;
import com.swirlds.config.api.ConfigurationBuilder;

/**
 * Looks for, and loads, the software version information for this instance of this node. There *MUST* be a
 * semantic-version.properties file in the classpath, or an environment variable pointing to a valid file. That
 * file MUST contain the following properties:
 *
 * <ul>
 *     <li>{@code version.services}</li>
 *     <li>{@code version.hapi}</li>
 * </ul>
 *
 * <p>Those properties *MUST* be properly formatted semantic versions. If the version information is not found, then an
 * exception is thrown because we CANNOT start the node without the appropriate version information.
 */
public final class SoftwareVersionLoader {

    private SoftwareVersionLoader() {
        // Do not instantiate
    }

    /**
     * Loads the software version information from the classpath.
     *
     * @return the software version information specific to this build of the software.
     */
    public static HederaSoftwareVersion load() {
        try {
            // I'm reusing the configuration machinery for this task, since it does what I need
            final var config = ConfigurationBuilder.create()
                    .withSource(new PropertyConfigSource("semantic-version.properties", 500))
                    .withConfigDataType(VersionConfig.class)
                    .withConverter(new SemanticVersionConverter())
                    .build();
            final var data = config.getConfigData(VersionConfig.class);
            return new HederaSoftwareVersion(data.hapiVersion(), data.servicesVersion());
        } catch (final Exception e) {
            throw new IllegalStateException("Can not create config source for semantic version properties", e);
        }
    }
}
