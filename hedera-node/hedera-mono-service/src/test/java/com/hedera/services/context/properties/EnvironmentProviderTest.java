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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

class EnvironmentProviderTest {

    private static final String PATH_PROPERTY = PropertyNames.NETTY_TLS_CERT_PATH;
    private static final String NETTY_PATH = "/opt/netty";
    private static final String BOOLEAN_PROPERTY = PropertyNames.CONTRACTS_ITEMIZE_STORAGE_FEES;
    private static final String LONG_PROPERTY = PropertyNames.BOOTSTRAP_RATES_NEXT_EXPIRY;

    private static final String SINGLE_QUOTE_TEMPLATE = "'%s'";

    private static final String DOUBLE_QUOTE_TEMPLATE = "\"%s\"";

    // Normally there would be tests for the getVars() instance method as well as the static,
    // but since it loads properties from System.getenv() but has no setter, we'll
    // have to settle for tests of the static method only. The instance method is a passthrough,
    // however, so we shouldn't miss out on much

    @Test
    void outerSingleQuotesGiven() {
        var configInput =
                newNodeConfig(
                        String.format(SINGLE_QUOTE_TEMPLATE, newPair(PATH_PROPERTY, NETTY_PATH)));
        var result = EnvironmentProvider.getDefinedVariables(List.of(PATH_PROPERTY), configInput);
        assertEquals(NETTY_PATH, result.get(PATH_PROPERTY));
    }

    @Test
    void outerDoubleQuotesGiven() {
        var configInput = String.format(DOUBLE_QUOTE_TEMPLATE, newPair(PATH_PROPERTY, NETTY_PATH));
        var result =
                EnvironmentProvider.getDefinedVariables(
                        List.of(PATH_PROPERTY), newNodeConfig(configInput));
        assertEquals(NETTY_PATH, result.get(PATH_PROPERTY));
    }

    @Test
    void noOuterQuotesGiven() {
        var configInput = newPair(PATH_PROPERTY, NETTY_PATH);
        var result =
                EnvironmentProvider.getDefinedVariables(
                        List.of(PATH_PROPERTY), newNodeConfig(configInput));
        assertEquals(NETTY_PATH, result.get(PATH_PROPERTY));
    }

    @Test
    void propertyValueSingleQuotesGiven() {
        var configInput =
                String.format(
                        SINGLE_QUOTE_TEMPLATE,
                        newPair(PATH_PROPERTY, String.format(SINGLE_QUOTE_TEMPLATE, NETTY_PATH)));
        var result =
                EnvironmentProvider.getDefinedVariables(
                        List.of(PATH_PROPERTY), newNodeConfig(configInput));
        assertEquals(NETTY_PATH, result.get(PATH_PROPERTY));
    }

    @Test
    void propertyValueDoubleQuotesGiven() {
        var configInput =
                String.format(
                        SINGLE_QUOTE_TEMPLATE,
                        newPair(PATH_PROPERTY, String.format(DOUBLE_QUOTE_TEMPLATE, NETTY_PATH)));
        var result =
                EnvironmentProvider.getDefinedVariables(
                        List.of(PATH_PROPERTY), newNodeConfig(configInput));
        assertEquals(NETTY_PATH, result.get(PATH_PROPERTY));
    }

    @Test
    void multiplePropertiesGiven() {
        var pathProp = newPair(PATH_PROPERTY, NETTY_PATH);
        var boolProp = newPair(BOOLEAN_PROPERTY, "true");
        var longProp = newPair(LONG_PROPERTY, "5_000");
        var configInput =
                String.format(DOUBLE_QUOTE_TEMPLATE, pathProp + " " + boolProp + " " + longProp);
        var result =
                EnvironmentProvider.getDefinedVariables(
                        List.of(PATH_PROPERTY, BOOLEAN_PROPERTY, LONG_PROPERTY),
                        newNodeConfig(configInput));
        assertEquals(NETTY_PATH, result.get(PATH_PROPERTY));
        assertEquals("true", result.get(BOOLEAN_PROPERTY));
        assertEquals("5_000", result.get(LONG_PROPERTY));
    }

    @Test
    void extraWhitespaceGiven() {
        var pathProp = newPair(PATH_PROPERTY, NETTY_PATH);
        var longProp = newPair(LONG_PROPERTY, "5_000");
        var configInput =
                String.format(
                        "\r\n\t   " + DOUBLE_QUOTE_TEMPLATE + "\n ",
                        pathProp + " \n\t\r\r " + longProp + "     ");
        var result =
                EnvironmentProvider.getDefinedVariables(
                        List.of(PATH_PROPERTY, LONG_PROPERTY), newNodeConfig(configInput));
        assertEquals(NETTY_PATH, result.get(PATH_PROPERTY));
        assertEquals("5_000", result.get(LONG_PROPERTY));
    }

    @Test
    void subsetOfSupportedOptionNamesAndAllEnvVarsGiven() {
        var pathProp = newPair(PATH_PROPERTY, NETTY_PATH);
        var booleanProp = newPair(BOOLEAN_PROPERTY, "false");
        var longProp = newPair(LONG_PROPERTY, "5_000");
        var configInput =
                String.format(SINGLE_QUOTE_TEMPLATE, pathProp + " " + booleanProp + " " + longProp);
        var result =
                EnvironmentProvider.getDefinedVariables(
                        List.of(PATH_PROPERTY, BOOLEAN_PROPERTY), newNodeConfig(configInput));
        assertEquals(NETTY_PATH, result.get(PATH_PROPERTY));
        assertEquals("false", result.get(BOOLEAN_PROPERTY));
        // This test handles the case where all 3 properties on the command line are present, but
        // the call will only request two of the option names, and thus the third command line
        // option should not be in the result
        assertEquals(2, result.size());
    }

    @Test
    void latestEnvVarTakesPrecedence() {
        var firstBooleanProp = newPair(BOOLEAN_PROPERTY, "false");
        var lastBooleanProp2 = newPair(BOOLEAN_PROPERTY, "true");
        var configInput =
                String.format(SINGLE_QUOTE_TEMPLATE, firstBooleanProp + " " + lastBooleanProp2);
        var result =
                EnvironmentProvider.getDefinedVariables(
                        List.of(PATH_PROPERTY, BOOLEAN_PROPERTY), newNodeConfig(configInput));
        assertEquals("true", result.get(BOOLEAN_PROPERTY));
        assertEquals(1, result.size());
    }

    @Test
    void optionNameRequestedThatIsntInEnvVars() {
        var booleanProp = newPair(BOOLEAN_PROPERTY, "false");
        var configInput = String.format(SINGLE_QUOTE_TEMPLATE, booleanProp);
        var result =
                EnvironmentProvider.getDefinedVariables(
                        List.of(PATH_PROPERTY, BOOLEAN_PROPERTY), newNodeConfig(configInput));
        assertEquals("false", result.get(BOOLEAN_PROPERTY));
        assertEquals(1, result.size());
    }

    @Test
    void emptyEnvVarComesBackUndefined() {
        var pathProp = newPair(PATH_PROPERTY, NETTY_PATH);
        var booleanProp = newPair(BOOLEAN_PROPERTY, StringUtils.EMPTY);
        var configInput = String.format(SINGLE_QUOTE_TEMPLATE, pathProp + " " + booleanProp);
        var result =
                EnvironmentProvider.getDefinedVariables(
                        List.of(PATH_PROPERTY, BOOLEAN_PROPERTY), newNodeConfig(configInput));
        assertEquals(NETTY_PATH, result.get(PATH_PROPERTY));
        assertEquals(1, result.size());
    }

    @Test
    void emptyOptionNamesGiven() {
        var result =
                EnvironmentProvider.getDefinedVariables(
                        List.of(), Map.of("HEDERA_CONFIG_NODE", "netty.tlsCrt.path=/opt/netty/"));
        assertTrue(result.isEmpty());
    }

    @Test
    void emptyEnvVarsGiven() {
        var result =
                EnvironmentProvider.getDefinedVariables(
                        List.of(PATH_PROPERTY, BOOLEAN_PROPERTY, LONG_PROPERTY), Map.of());
        assertTrue(result.isEmpty());
    }

    private static String newPair(String name, String value) {
        return String.format("%s=%s", name, value);
    }

    private static Map<String, String> newNodeConfig(String config) {
        return Map.of("HEDERA_NODE_CONFIG", config);
    }
}
