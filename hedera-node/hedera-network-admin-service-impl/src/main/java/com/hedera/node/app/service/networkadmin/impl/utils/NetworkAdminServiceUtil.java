/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl.utils;

import static java.lang.System.arraycopy;

import com.google.common.primitives.Longs;
import com.hedera.hapi.node.base.AccountID;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides utility methods for network admin operations.
 */
public final class NetworkAdminServiceUtil {
    private NetworkAdminServiceUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Function that converts Account ID to Hexed EVM address.
     * @param accountId Account ID to be converted
     * @return Returns string hexed EVM address for the given account ID
     */
    @NonNull
    public static String asHexedEvmAddress(@NonNull final AccountID accountId) {
        return CommonUtils.hex(asEvmAddress(accountId.accountNum()));
    }

    @NonNull
    private static byte[] asEvmAddress(final long num) {
        final byte[] evmAddress = new byte[20];
        arraycopy(Longs.toByteArray(num), 0, evmAddress, 12, 8);
        return evmAddress;
    }
}
