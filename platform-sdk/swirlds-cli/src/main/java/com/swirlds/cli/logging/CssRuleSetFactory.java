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

package com.swirlds.cli.logging;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * Creates a CSS rule set
 * <p>
 * A rule set consists of a selector, followed by a list of declarations:
 * <p>
 * "selector { property1: value; property2: value2; }"
 */
public class CssRuleSetFactory {
    /**
     * The selector for the rule set
     */
    private final String selector;
    /**
     * The declarations for the rule set
     */
    private final List<CssDeclaration> declarations;

    /**
     * Constructor
     *
     * @param selector     the selector
     * @param declarations the declarations
     */
    public CssRuleSetFactory(@NonNull final String selector, @NonNull final List<CssDeclaration> declarations) {
        this.selector = Objects.requireNonNull(selector);
        this.declarations = Objects.requireNonNull(declarations);
    }

    /**
     * Generate the CSS string
     *
     * @return the CSS string
     */
    @NonNull
    public String generateCss() {
        return selector + " {\n"
                + String.join(
                        "\n",
                        declarations.stream().map(CssDeclaration::toString).toList())
                + "\n}";
    }
}
