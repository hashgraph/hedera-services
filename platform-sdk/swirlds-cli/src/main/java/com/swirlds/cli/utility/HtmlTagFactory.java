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
    public static final String HTML_HTML_TAG = "html";
    public static final String HTML_H3_TAG = "h3";
    public static final String HTML_SCRIPT_TAG = "script";
    public static final String HTML_LABEL_TAG = "label";
    public static final String HTML_INPUT_TAG = "input";
    public static final String HTML_BODY_TAG = "body";
    public static final String HTML_TABLE_TAG = "table";
    public static final String HTML_ROW_TAG = "tr";
    public static final String HTML_DATA_CELL_TAG = "td";
    public static final String HTML_SPAN_TAG = "span";
    public static final String HTML_HEAD_TAG = "head";
    public static final String HTML_STYLE_TAG = "style";
    public static final String HTML_BREAK_TAG = "br";

    public static final String HTML_CLASS_ATTRIBUTE = "class";
    public static final String HTML_SOURCE_ATTRIBUTE = "src";
    public static final String HTML_TYPE_ATTRIBUTE = "type";

    public static final String HTML_CHECKBOX_TYPE = "checkbox";

    public static final String HIDEABLE_LABEL = "hideable";
    public static final String NODE_ID_COLUMN_LABEL = "node-id";
    public static final String ELAPSED_TIME_COLUMN_LABEL = "elapsed-time";
    public static final String TIMESTAMP_COLUMN_LABEL = "timestamp";
    public static final String LOG_NUMBER_COLUMN_LABEL = "log-number";
    public static final String LOG_LEVEL_COLUMN_LABEL = "log-level";
    public static final String MARKER_COLUMN_LABEL = "marker";
    public static final String THREAD_NAME_COLUMN_LABEL = "thread-name";
    public static final String CLASS_NAME_COLUMN_LABEL = "class-name";
    public static final String REMAINDER_OF_LINE_COLUMN_LABEL = "remainder-of-line";

    public static final String FILTER_HEADING_LABEL = "filter-heading";
    public static final String HIDER_LABEL = "hider";
    public static final String HIDER_LABEL_LABEL = "hider-label";
    public static final String LOG_BODY_LABEL = "log-body";

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
