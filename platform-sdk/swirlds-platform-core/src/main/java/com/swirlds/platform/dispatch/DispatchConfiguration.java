/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.dispatch;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for dispatches and the dispatch builder.
 *
 * @param flowchartEnabled
 * 		if true then generate a visual flowchart showing the dispatch configuration of platform components
 * @param flowchartTriggerWhitelist
 * 		a whitelist of trigger types when building the dispatch flowchart, ":" separated
 * @param flowchartTriggerBlacklist
 * 		a blacklist of trigger types when building the dispatch flowchart, ":" separated
 * @param flowchartObjectWhitelist
 * 		a whitelist of observer/dispatcher types when building the dispatch flowchart, ":" separated
 * @param flowchartObjectBlacklist
 * 		a blacklist of observer/dispatcher types when building the dispatch flowchart, ":" separated
 */
@ConfigData("dispatch")
public record DispatchConfiguration(
        @ConfigProperty(defaultValue = "false") boolean flowchartEnabled,
        @ConfigProperty(defaultValue = "") String flowchartTriggerWhitelist,
        @ConfigProperty(defaultValue = "") String flowchartTriggerBlacklist,
        @ConfigProperty(defaultValue = "") String flowchartObjectWhitelist,
        @ConfigProperty(defaultValue = "") String flowchartObjectBlacklist) {

    /**
     * @return a set of all whitelisted flowchart triggers
     */
    public Set<String> getFlowchartTriggerWhitelistSet() {
        return parseStringList(flowchartTriggerWhitelist);
    }

    /**
     * @return a set of all blacklisted flowchart triggers
     */
    public Set<String> getFlowchartTriggerBlacklistSet() {
        return parseStringList(flowchartTriggerBlacklist);
    }

    /**
     * @return a set of all whitelisted flowchart objects
     */
    public Set<String> getFlowchartObjectWhitelistSet() {
        return parseStringList(flowchartObjectWhitelist);
    }

    /**
     * @return a set of all blacklisted flowchart objects
     */
    public Set<String> getFlowchartObjectBlacklistSet() {
        return parseStringList(flowchartObjectBlacklist);
    }

    /**
     * Parse a ":" delimited list of strings.
     */
    private static Set<String> parseStringList(final String commaSeparatedStrings) {
        final Set<String> strings = new HashSet<>();
        if (!commaSeparatedStrings.equals("")) {
            for (final String string : commaSeparatedStrings.split(":")) {
                if (!Objects.equals(string, "")) {
                    strings.add(string);
                }
            }
        }
        return strings;
    }
}
