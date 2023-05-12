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

package com.swirlds.common.metrics.platform.prometheus;

import com.swirlds.common.utility.CommonUtils;

/**
 * Converter that ensures a label satisfies Prometheus requirements
 * (only alphanumeric characters, '_', and ':')
 */
public final class NameConverter {

    private NameConverter() {}

    /**
     * Returns a {@link String} where all illegal characters are replaced (according to a simple heuristics).
     *
     * @param label
     * 		The input-{@link String}
     * @return The resulting {@link String}
     * @throws IllegalArgumentException if {@code label} is {@code null}
     */
    public static String fix(final String label) {
        CommonUtils.throwArgNull(label, "label");
        return label.strip()
                .replace('.', ':')
                .replace('-', '_')
                .replace(' ', '_')
                .replace("/", "_per_")
                .replace("%", "Percent")
                .replaceAll("[^\\w:]", "");
    }
}
