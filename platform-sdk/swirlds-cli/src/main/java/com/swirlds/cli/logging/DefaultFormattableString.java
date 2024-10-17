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

package com.swirlds.cli.logging;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Default implementation of {@link FormattableString}. Doesn't do any formatting.
 */
public class DefaultFormattableString implements FormattableString {
    /**
     * The original string.
     */
    private final String originalString;

    /**
     * Constructor.
     *
     * @param inputString the input string
     */
    public DefaultFormattableString(@NonNull final String inputString) {
        originalString = inputString;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getOriginalPlaintext() {
        return originalString;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String generateAnsiString() {
        return originalString;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String generateHtmlString() {
        return LogProcessingUtils.escapeString(originalString);
    }
}
