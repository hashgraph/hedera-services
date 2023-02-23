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

package com.hedera.node.app.service.evm.accounts;

import com.google.common.base.Suppliers;
import java.util.Arrays;
import java.util.function.Supplier;
import org.hyperledger.besu.datatypes.Address;

public abstract class HederaEvmContractAliases {

    public static final int EVM_ADDRESS_LEN = 20;
    private static final Supplier<byte[]> MIRROR_PREFIX = Suppliers.memoize(() -> {
        /* A placeholder to store the 12-byte of zeros prefix that marks an EVM
         * address as a "mirror" address. */
        return new byte[12];
    });

    public abstract Address resolveForEvm(Address addressOrAlias);

    public boolean isMirror(final Address address) {
        return isMirror(address.toArrayUnsafe());
    }

    public static boolean isMirror(final byte[] address) {
        if (address.length != EVM_ADDRESS_LEN) {
            return false;
        }

        return Arrays.equals(MIRROR_PREFIX.get(), 0, 12, address, 0, 12);
    }
}
