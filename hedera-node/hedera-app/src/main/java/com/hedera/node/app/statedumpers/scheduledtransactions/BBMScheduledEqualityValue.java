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

package com.hedera.node.app.statedumpers.scheduledtransactions;

import java.util.Map;
import java.util.SortedMap;

@SuppressWarnings("java:S6218")
public // "Equals/hashcode methods should be overridden in records containing array fields"
record BBMScheduledEqualityValue(SortedMap<String, Long> ids) {

    @Override
    public String toString() {
        return "BBMScheduledEqualityValue{" + "ids=" + idsToString(ids) + '}';
    }

    public static String idsToString(SortedMap<String, Long> ids) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (Map.Entry<String, Long> entry : ids.entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();
            sb.append(key).append(": ").append(value).append(", ");
        }
        if (!ids.isEmpty()) {
            // Remove the trailing comma and space
            sb.setLength(sb.length() - 2);
        }
        sb.append("}");
        return sb.toString();
    }
}
