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

package com.swirlds.platform.test.chatter.simulator;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Misc. utility methods used by gossip simulation.
 */
public final class GossipSimulationUtils {

    private static final String HEADER_STRING = "-------------------";

    private GossipSimulationUtils() {}

    /**
     * Write a header in standard format.
     *
     * @param header
     * 		the content of the header
     */
    public static void printHeader(final String header) {
        System.out.println(HEADER_STRING + " " + header + " " + HEADER_STRING);
    }

    /**
     * Convert a double to a formatted string with a limited number of decimal places.
     *
     * @param value
     * 		the value to format
     * @param decimalPlaces
     * 		the maximum number of decimal places
     * @return a formatted string
     */
    public static String roundDecimal(final Double value, final int decimalPlaces) {
        if (value.isNaN()) {
            return "NaN";
        }
        return BigDecimal.valueOf(value)
                .setScale(decimalPlaces, RoundingMode.HALF_UP)
                .toString();
    }
}
