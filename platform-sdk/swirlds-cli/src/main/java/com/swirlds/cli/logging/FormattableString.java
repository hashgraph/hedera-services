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
 * A string that can be formatted.
 */
public interface FormattableString {
    /**
     * Gets the original plaintext version of the string.
     *
     * @return the plain text string
     */
    @NonNull
    String getOriginalPlaintext();

    /**
     * Generate a string with ANSI coloration.
     *
     * @return the string with ANSI coloration
     */
    @NonNull
    String generateAnsiString();

    /**
     * Generate a string with HTML formatting.
     * <p>
     * This method must return a string that is safe to be inserted into an HTML document
     *
     * @return the string with HTML formatting
     */
    @NonNull
    String generateHtmlString();
}
