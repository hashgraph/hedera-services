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

import static com.swirlds.cli.utility.HtmlGenerator.HTML_CLASS_ATTRIBUTE;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Class for constructing HTML tags.
 */
public class HtmlTagFactory {
    /**
     * This label is used to hold the value of how many filters are currently applied to a field
     * <p>
     * This is used to determine if the field should be hidden or not
     */
    public static final String DATA_HIDE_LABEL = "data-hide";

    /**
     * Map from attribute name to list of attribute values.
     */
    private final Map<String, List<String>> attributeMap = new HashMap<>();

    /**
     * The type of HTML tag
     */
    private final String tagName;

    /**
     * The content of the HTML tag. May be empty
     */
    private final String content;

    /**
     * If true then the tag is a void tag
     */
    boolean isVoidTag = false;

    /**
     * Construct a new HtmlTagFactory.
     *
     * @param tagName The type of HTML tag
     * @param content The content of the HTML tag. Must be null if voidTag is true
     * @param voidTag If the tag is a void tag
     */
    public HtmlTagFactory(@NonNull final String tagName, @Nullable final String content, final boolean voidTag) {
        this.tagName = Objects.requireNonNull(tagName);

        if (voidTag && content != null) {
            throw new IllegalArgumentException("content must be null if voidTag is true");
        }

        this.content = content;
        this.isVoidTag = voidTag;
    }

    /**
     * Add an attribute with multiple values.
     * <p>
     * If the attribute has already been added, values will be appended to the existing value list.
     *
     * @param attributeName The attribute name
     * @param values        The attribute values
     * @return this
     */
    public HtmlTagFactory addAttribute(@NonNull final String attributeName, @NonNull final List<String> values) {
        if (attributeMap.containsKey(attributeName)) {
            attributeMap.get(attributeName).addAll(values);
        } else {
            attributeMap.put(attributeName, values);
        }

        return this;
    }

    /**
     * Add an attribute with a single value.
     * <p>
     * If the attribute has already been added, the value will be appended to the existing value list.
     *
     * @param attributeName The attribute name
     * @param value         The attribute value
     * @return this
     */
    public HtmlTagFactory addAttribute(@NonNull final String attributeName, @NonNull final String value) {
        if (attributeMap.containsKey(attributeName)) {
            attributeMap.get(attributeName).add(value);
        } else {
            attributeMap.put(attributeName, List.of(value));
        }

        return this;
    }

    /**
     * Convenience method for adding a class attribute.
     *
     * @param className The class name
     * @return this
     */
    public HtmlTagFactory addClass(@NonNull final String className) {
        return addAttribute(HTML_CLASS_ATTRIBUTE, className);
    }

    /**
     * Convenience method for adding multiple class attributes.
     *
     * @param classNames The class names
     * @return this
     */
    public HtmlTagFactory addClasses(@NonNull final List<String> classNames) {
        return addAttribute(HTML_CLASS_ATTRIBUTE, classNames);
    }

    /**
     * Generate the HTML tag.
     *
     * @return the HTML tag
     */
    public String generateTag() {
        final StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("<");
        stringBuilder.append(tagName);

        final List<String> attributeStrings = new ArrayList<>();
        for (final Map.Entry<String, List<String>> entry : attributeMap.entrySet()) {
            final String attributeStringBuilder = entry.getKey() + "=\"" + String.join(" ", entry.getValue()) + "\"";

            attributeStrings.add(attributeStringBuilder);
        }

        if (!attributeStrings.isEmpty()) {
            stringBuilder.append(" ");
            stringBuilder.append(String.join(" ", attributeStrings));
        }

        if (isVoidTag) {
            stringBuilder.append(">");
            return stringBuilder.toString();
        }

        stringBuilder.append(">");
        stringBuilder.append(content);
        stringBuilder.append("</");
        stringBuilder.append(tagName);
        stringBuilder.append(">");

        return stringBuilder.toString();
    }
}
