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
package com.hedera.node.app.service.token.util;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.ledger.accounts.AbstractContractAliases.EVM_ADDRESS_LEN;
import static com.hedera.services.utils.EntityIdUtils.numFromEvmAddress;

import com.google.common.primitives.Longs;
import java.util.Arrays;

/** Utility class needed for resolving aliases */
public final class AliasUtils {
    public static final Long MISSING_NUM = 0L;

    private AliasUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isMirror(final byte[] address) {
        if (address.length != EVM_ADDRESS_LEN) {
            return false;
        }
        byte[] mirrorPrefix = new byte[12];
        System.arraycopy(Longs.toByteArray(STATIC_PROPERTIES.getShard()), 4, mirrorPrefix, 0, 4);
        System.arraycopy(Longs.toByteArray(STATIC_PROPERTIES.getRealm()), 0, mirrorPrefix, 4, 8);

        return Arrays.equals(mirrorPrefix, 0, 12, address, 0, 12);
    }

    public static Long fromMirror(final byte[] evmAddress) {
        return numFromEvmAddress(evmAddress);
    }
}
