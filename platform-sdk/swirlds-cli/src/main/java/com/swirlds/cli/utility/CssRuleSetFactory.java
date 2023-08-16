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

package com.swirlds.cli.utility;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * Creates a CSS rule set
 */
public class CssRuleSetFactory {
    public static final String DISPLAY_PROPERTY = "display";
    public static final String FONT_PROPERTY = "font-family";
    public static final String BACKGROUND_COLOR_PROPERTY = "background-color";
    public static final String TEXT_COLOR_PROPERTY = "color";
    public static final String WHITE_SPACE_PROPERTY = "white-space";
    public static final String PADDING_LEFT_PROPERTY = "padding-left";
    public static final String OVERFLOW_WRAP_PROPERTY = "overflow-wrap";
    public static final String MAX_WIDTH_PROPERTY = "max-width";
    public static final String VERTICAL_ALIGN_PROPERTY = "vertical-align";
    public static final String WORD_BREAK_PROPERTY = "word-break";

    public static final String NO_WRAP_VALUE = "nowrap";
    public static final String NORMAL_VALUE = "normal";
    public static final String BREAK_WORD_VALUE = "break-word";
    public static final String TOP_VALUE = "top";

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
     * Constructor for rule sets with only 1 declaration
     *
     * @param selector    the selector
     * @param declaration the declaration
     */
    public CssRuleSetFactory(@NonNull final String selector, @NonNull final CssDeclaration declaration) {
        this(selector, List.of(declaration));
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
