/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring.model;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Given a class, derive a hyperlink to the github for that class's source code (in develop).
 */
public final class HyperlinkBuilder {

    public static final String PLATFORM_CORE_ROOT = "https://github.com/hashgraph/hedera-services/blob/develop/"
            + "platform-sdk/swirlds-platform-core/src/main/java";

    public static final String PLATFORM_COMMON_ROOT =
            "https://github.com/hashgraph/hedera-services/blob/develop/platform-sdk/swirlds-common/src/main/java";

    /**
     * Build a hyperlink to the platform core source code for the given class. Only works for things in the core
     * platform module.
     *
     * @param clazz the class
     * @return the hyperlink
     */
    public static String platformCoreHyperlink(@NonNull final Class<?> clazz) {
        return buildHyperlink(PLATFORM_CORE_ROOT, clazz);
    }

    /**
     * Build a hyperlink to the platform common source code for the given class. Only works for things in the common
     * platform module.
     *
     * @param clazz the class
     * @return the hyperlink
     */
    public static String platformCommonHyperlink(@NonNull final Class<?> clazz) {
        return buildHyperlink(PLATFORM_COMMON_ROOT, clazz);
    }

    /**
     * Get a github hyperlink to this class (in develop).
     *
     * @param clazz the class
     * @return the hyperlink
     */
    @NonNull
    public static String buildHyperlink(@NonNull final String root, @NonNull final Class<?> clazz) {

        final String className = clazz.getName();
        final String[] parts = className.split("\\.");

        final StringBuilder sb = new StringBuilder();
        sb.append(root);
        if (!root.endsWith("/")) {
            sb.append("/");
        }
        for (int i = 0; i < parts.length; i++) {
            sb.append(parts[i]);
            if (i < parts.length - 1) {
                sb.append("/");
            }
        }
        sb.append(".java");

        return sb.toString();
    }
}
