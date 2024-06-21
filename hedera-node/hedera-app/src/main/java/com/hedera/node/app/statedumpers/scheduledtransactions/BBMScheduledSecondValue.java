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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

@SuppressWarnings("java:S6218")
public record BBMScheduledSecondValue(NavigableMap<Instant, List<Long>> ids) {

    @Override
    public String toString() {
        return "BBMScheduledSecondValue{" + "ids=" + idsToString(ids) + '}';
    }

    public static String idsToString(NavigableMap<Instant, List<Long>> ids) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (Map.Entry<Instant, List<Long>> entry : ids.entrySet()) {
            Instant key = entry.getKey();
            List<Long> values = entry.getValue();
            sb.append(key).append(": ").append(values).append(", ");
        }
        if (!ids.isEmpty()) {
            // Remove the trailing comma and space
            sb.setLength(sb.length() - 2);
        }
        sb.append("}");
        return sb.toString();
    }
}
