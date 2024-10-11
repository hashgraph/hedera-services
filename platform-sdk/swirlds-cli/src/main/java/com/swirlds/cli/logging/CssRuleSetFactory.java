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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for turning CSS rules into a single CSS string, for inclusion in an HTML page.
 */
public class CssRuleSetFactory {
    /**
     * The rules that will be part of the output CSS string.
     */
    private final List<String> rules = new ArrayList<>();

    /**
     * Create a string representing a CSS rule.
     *
     * @return the CSS rule string
     */
    @NonNull
    private static String createRuleString(
            @NonNull final String selector, @NonNull final List<CssDeclaration> declarations) {
        return selector + " {\n"
                + String.join(
                        "\n",
                        declarations.stream().map(CssDeclaration::toString).toList())
                + "\n}";
    }

    /**
     * Add a rule that will be part of the output CSS.
     *
     * @param selector     the CSS selector
     * @param declarations the CSS declarations
     */
    public void addRule(@NonNull final String selector, @NonNull final CssDeclaration... declarations) {
        Objects.requireNonNull(selector);
        Objects.requireNonNull(declarations);

        rules.add(createRuleString(selector, Arrays.asList(declarations)));
    }

    /**
     * Generate the CSS string representing all the rules that have been added.
     *
     * @return the CSS string
     */
    @NonNull
    public String generateCss() {
        return String.join("\n", rules);
    }
}
