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

package com.swirlds.base.utility;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * A builder for toString methods.
 */
public class ToStringBuilder {
    private final StringBuilder builder;
    private final Class<?> thisClass;
    private final Class<?> superClass;
    private final int initLength;

    public ToStringBuilder(@NonNull final Object object) {
        builder = new StringBuilder();
        thisClass = object.getClass();
        superClass = thisClass.getSuperclass();

        builder.append(thisClass.getSimpleName()).append("[");
        initLength = builder.length();
    }

    private ToStringBuilder(@NonNull final String superString) {
        builder = new StringBuilder();
        builder.append(superString);

        initLength = builder.length();
        superClass = null;
        thisClass = null;
    }

    public ToStringBuilder appendSuper(@NonNull String superString) {
        Objects.requireNonNull(superString, "superString must not be null");
        final String replacedString = superString.replaceFirst(thisClass.getSimpleName(), superClass.getSimpleName());
        builder.append(new ToStringBuilder(replacedString)).append(",");
        return this;
    }

    @NonNull
    public ToStringBuilder append(@Nullable Object value) {
        builder.append(value).append(",");
        return this;
    }

    @NonNull
    public ToStringBuilder append(@NonNull String fieldName, @Nullable Object value) {
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        builder.append(fieldName).append("=").append(value).append(",");
        return this;
    }

    @Override
    @NonNull
    public String toString() {
        if (builder.length() > initLength) {
            builder.setLength(builder.length() - 1); // Remove last comma
        }
        if (thisClass != null) {
            builder.append("]");
        }
        return builder.toString();
    }

    @NonNull
    public String build() {
        return toString();
    }
}
