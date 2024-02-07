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

package com.swirlds.base.sample.config.internal;

import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.config.extensions.sources.AbstractConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link ConfigSource} that can read files in the application classpath.
 */
public class ClasspathConfigSource extends AbstractConfigSource {
    private static final Logger log = LogManager.getLogger(ClasspathConfigSource.class);
    private final @NonNull Map<String, String> internalProperties;

    public ClasspathConfigSource(final @NonNull Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath can not be null");
        this.internalProperties = new HashMap<>();
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(filePath.toString());
        assert inputStream != null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            Properties loadedProperties = new Properties();
            loadedProperties.load(reader);
            loadedProperties
                    .stringPropertyNames()
                    .forEach((name) -> this.internalProperties.put(name, loadedProperties.getProperty(name)));
        } catch (Exception e) {
            log.error("Could not load properties from classpath resource {}", filePath);
            throw e;
        }
    }

    @Override
    protected @NonNull Map<String, String> getInternalProperties() {
        return internalProperties;
    }
}
