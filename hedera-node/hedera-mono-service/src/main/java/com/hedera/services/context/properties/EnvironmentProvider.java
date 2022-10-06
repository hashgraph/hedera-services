/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.properties;

import com.google.common.collect.ImmutableMap;
import java.util.*;
import org.apache.commons.lang3.StringUtils;

/** Wrapper class for system environment properties */
public class EnvironmentProvider {

    private final Map<String, String> envVars;

    private EnvironmentProvider() {
        this.envVars = ImmutableMap.copyOf(System.getenv());
    }

    private static final String HEDERA_NODE_CONFIG_NAME = "HEDERA_NODE_CONFIG";

    /** Instance method for convenience. See getVars(Collection, Map) */
    public ImmutableMap<String, String> getDefinedVariables(Collection<String> optionNames) {
        return getDefinedVariables(optionNames, envVars);
    }

    /**
     * Returns the defined environment variables from a given set of requested (supported) option
     * names and a given map of environment variables. In other words, the intersection of the
     * option names and the variables present in the HEDERA_NODE_CONFIG_NAME environment variable is
     * calculated and returned.
     *
     * <p>E.g. if optionNames is a list containing "tokens.nfts.areEnabled" and "files.maxSizeKb",
     * and envVars contains the key-value mapping HEDERA_NODE_CONFIG -> "tokens.nfts.areEnabled=true
     * contracts.chainId=25", the result would be a map with a single key-value mapping of
     * "tokens.nfts.areEnabled" -> "true". The "files.maxSizeKb" property would be excluded because
     * it isn't defined in the given envVars map, and "contracts.chainId" would be excluded because
     * "contracts.chainId" was not in the requested option names (even though "contracts.chainId"
     * was defined in the HEDERA_NODE_CONFIG environment variable).
     */
    public static ImmutableMap<String, String> getDefinedVariables(
            Collection<String> requestedPropertyNames, Map<String, String> envVars) {
        final var parsedEnvVarProperties = parsePropertiesPresentInEnvVar(envVars);
        final var found = new HashMap<String, String>();
        // This loop will calculate the intersection of key-value pairs where the environment var is
        // defined in envVars AND the option name is in optionNames
        for (String requestedPropertyName : requestedPropertyNames) {
            final var parsedPropertyValue = parsedEnvVarProperties.get(requestedPropertyName);
            if (StringUtils.isNotBlank(parsedPropertyValue)) {
                found.put(requestedPropertyName, parsedPropertyValue);
            }
        }

        return ImmutableMap.copyOf(found);
    }

    public static EnvironmentProvider getInstance() {
        if (instance == null) {
            instance = new EnvironmentProvider();
        }

        return instance;
    }

    private static Map<String, String> parsePropertiesPresentInEnvVar(Map<String, String> envVars) {
        if (!envVars.containsKey(HEDERA_NODE_CONFIG_NAME)) {
            return Map.of();
        }

        // Note: envVarValue is the value of everything inside the environment variable, not the
        // value of a single property
        final var envVarValue = stripOuterQuotes(envVars.get(HEDERA_NODE_CONFIG_NAME).trim());
        final var whitespaceDelimitedPropValuePairs = envVarValue.split("\s+");
        final var parsed = new HashMap<String, String>();
        for (String propValuePair : whitespaceDelimitedPropValuePairs) {
            if (propValuePair.isBlank()) {
                continue;
            }

            final var parts = propValuePair.trim().split("=");
            final var propName = (parts.length > 0 ? parts[0] : StringUtils.EMPTY);
            final var propValue =
                    stripOuterQuotes((parts.length > 1 ? parts[1] : StringUtils.EMPTY));
            if (propName.length() > 0
                    && propValue.length() > 0) { // null or empty overrides aren't allowed
                parsed.put(propName, propValue);
            }
        }

        return ImmutableMap.copyOf(parsed);
    }

    private static String stripOuterQuotes(String s) {
        if (StringUtils.isBlank(s)) {
            return s;
        }

        final var trimmed = s.trim();
        final var startChar = trimmed.charAt(0);
        final var endChar = trimmed.charAt(trimmed.length() - 1);
        if (startChar == '\'' || startChar == '"' || endChar == '\'' || endChar == '"') {
            return trimmed.substring(1, trimmed.length() - 1);
        } else {
            return s;
        }
    }

    private static EnvironmentProvider instance = null;
}
