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

import static com.swirlds.logging.utils.DataUnit.BYTE;
import static com.swirlds.logging.utils.DataUnit.GIGA_BYTE;
import static com.swirlds.logging.utils.DataUnit.KILO_BYTE;
import static com.swirlds.logging.utils.DataUnit.MEGA_BYTE;
import static com.swirlds.logging.utils.DataUnit.TERA_BYTE;

public record DataSize(long value, DataUnit unit) {

    public static DataSize parseFrom(String value) {
        final String result = value.replaceAll("\\s", "");
        DataUnit unit =
                switch (result.charAt(result.length() - 3)) {
                    case 'k':
                        yield KILO_BYTE;
                    case 'm':
                        yield MEGA_BYTE;
                    case 'g':
                        yield GIGA_BYTE;
                    case 't':
                        yield TERA_BYTE;
                    default:
                        yield BYTE;
                };

        return new DataSize(unit.getValueFromString(result), unit);
    }

    public long asBytes() {
        return this.unit.convertToBytes(this.value);
    }
}
