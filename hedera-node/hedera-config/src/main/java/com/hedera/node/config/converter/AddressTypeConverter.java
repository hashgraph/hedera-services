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
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * Implementation of {@link ConfigConverter} that supports {@link Address} as data type for the config.
 */
public class AddressTypeConverter implements ConfigConverter<Address> {

    @Override
    public Address convert(final String value) throws IllegalArgumentException, NullPointerException {
        if (value == null) {
            throw new NullPointerException("Value must not be null");
        }
        final var longFromString = Long.parseLong(value);
        if (longFromString <= 0) {
            throw new IllegalArgumentException("Value must be greater than 0");
        }
        return asLongZeroAddress(longFromString);
    }

    private static Address asLongZeroAddress(final long num) {
        return Address.wrap(Bytes.wrap(asEvmAddress(num)));
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
