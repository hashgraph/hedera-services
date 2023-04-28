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

package com.swirlds.common.config.sources;

import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Represents a mapping between an original name and a mapped name. This class is used to hold the mapping configuration
 * between an original name and a mapped name.
 *
 * @param mappedName   new property name
 * @param originalName original property name
 */
public record ConfigMapping(@NonNull String mappedName, @NonNull String originalName) {

    private static final Logger logger = LogManager.getLogger(MappedConfigSource.class);

    /**
     * Creates a new {@code ConfigMapping}
     *
     * @param mappedName   new property name
     * @param originalName original property name
     * @throws IllegalArgumentException If {@code mappedName} and {@code originalName} are equal
     */
    public ConfigMapping {
        CommonUtils.throwArgBlank(mappedName, "mappedName");
        CommonUtils.throwArgBlank(originalName, "originalName");
        if (Objects.equals(originalName, mappedName)) {
            throw new IllegalArgumentException(
                    "originalName and mappedName are the same (%s)! Will not create an mappedName"
                            .formatted(mappedName));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "'" + mappedName + "'<->'" + originalName + "'";
    }
}
