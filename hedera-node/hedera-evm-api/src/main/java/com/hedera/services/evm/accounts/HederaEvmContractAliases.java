/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.evm.accounts;

import com.google.common.base.Suppliers;
import com.google.common.primitives.Longs;
import com.hedera.services.evm.contracts.execution.StaticProperties;
import java.util.Arrays;
import java.util.function.Supplier;
import org.hyperledger.besu.datatypes.Address;

public abstract class HederaEvmContractAliases {

    public static final int EVM_ADDRESS_LEN = 20;

    /* A placeholder to store the 12-byte prefix (4-byte shard and 8-byte realm) that marks an EVM
     * address as a "mirror" address that follows immediately from a <shard>.<realm>.<num> id. */
    private static byte[] MIRROR_PREFIX = null;

    private static final Supplier<Long> SHARD = Suppliers.memoize(StaticProperties::getShard);
    private static final Supplier<Long> REALM = Suppliers.memoize(StaticProperties::getRealm);

    public abstract Address resolveForEvm(Address addressOrAlias);

    public boolean isMirror(final Address address) {
        return isMirror(address.toArrayUnsafe());
    }

    public boolean isMirror(final byte[] address) {
        if (address.length != EVM_ADDRESS_LEN) {
            return false;
        }
        if (MIRROR_PREFIX == null) {
            MIRROR_PREFIX = new byte[12];
            System.arraycopy(Longs.toByteArray(SHARD.get()), 4, MIRROR_PREFIX, 0, 4);
            System.arraycopy(Longs.toByteArray(REALM.get()), 0, MIRROR_PREFIX, 4, 8);
        }
        return Arrays.equals(MIRROR_PREFIX, 0, 12, address, 0, 12);
    }
}
