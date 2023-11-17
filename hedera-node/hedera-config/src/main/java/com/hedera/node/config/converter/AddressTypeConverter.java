/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.config.converter;

import com.swirlds.config.api.converter.ConfigConverter;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * Implementation of {@link ConfigConverter} that supports {@link Address} as data type for the config.
 */
public class AddressTypeConverter implements ConfigConverter<Set<Address>> {

    @Override
    public Set<Address> convert(final String value) throws IllegalArgumentException, NullPointerException {
        if (value == null) {
            throw new NullPointerException("Value must not be null");
        }
        return Arrays.stream(value.split(","))
                .filter(string -> !string.isBlank())
                .map(AddressTypeConverter::asLongZeroAddressFromString)
                .collect(Collectors.toSet());
    }

    private static Address asLongZeroAddressFromString(final String value) {
        final var longFromString = Long.parseLong(value);
        if (longFromString <= 0) {
            throw new IllegalArgumentException("Value must be greater than 0");
        }
        return Address.wrap(Bytes.wrap(asEvmAddress(longFromString)));
    }

    private static byte[] asEvmAddress(final long num) {
        final byte[] evmAddress = new byte[20];
        copyToLeftPaddedByteArray(num, evmAddress);
        return evmAddress;
    }

    private static void copyToLeftPaddedByteArray(long value, final byte[] dest) {
        for (int i = 7, j = dest.length - 1; i >= 0; i--, j--) {
            dest[j] = (byte) (value & 0xffL);
            value >>= 8;
        }
    }
}
