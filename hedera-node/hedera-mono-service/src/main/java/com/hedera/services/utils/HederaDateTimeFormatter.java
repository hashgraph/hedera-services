/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public final class HederaDateTimeFormatter {
    private HederaDateTimeFormatter() {
        throw new UnsupportedOperationException("Utility Class");
    }

    private static final DateTimeFormatter formatter =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd")
                    .appendLiteral('T')
                    .appendPattern("HH_mm_ss")
                    .appendLiteral('.')
                    .toFormatter()
                    .withZone(ZoneId.of("UTC"));

    public static String format(final Instant instant) {
        return formatter.format(instant) + String.format("%09d", instant.getNano()) + "Z";
    }
}
