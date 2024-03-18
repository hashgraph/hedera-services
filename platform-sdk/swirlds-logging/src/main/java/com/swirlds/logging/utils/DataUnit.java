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

package com.swirlds.logging.utils;

import edu.umd.cs.findbugs.annotations.NonNull;

public enum DataUnit {
    BYTE(""),
    KILO_BYTE("kb"),
    MEGA_BYTE("mb"),
    GIGA_BYTE("gb"),
    TERA_BYTE("tb");

    final String symbol;

    DataUnit(@NonNull final String symbol) {
        this.symbol = symbol;
    }

    public long convertToBytes(long value) {
        return value * (long) Math.pow(1024, ordinal());
    }

    public double convertFromBytes(double value) {
        return value / Math.pow(1024, ordinal());
    }

    public double convertTo(DataUnit targetUnit, long value) {
        return targetUnit.convertFromBytes(convertToBytes(value));
    }

    public String getSymbol() {
        return symbol;
    }

    public long getValueFromString(String value) {
        return Long.parseLong(value.substring(0, value.length() - symbol.length()));
    }
}
