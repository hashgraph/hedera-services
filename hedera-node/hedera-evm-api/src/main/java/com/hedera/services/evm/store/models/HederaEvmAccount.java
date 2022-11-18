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
package com.hedera.services.evm.store.models;

import com.google.protobuf.ByteString;
import com.hedera.services.ethereum.EthTxSigs;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class HederaEvmAccount {

    public static final ByteString ECDSA_KEY_ALIAS_PREFIX =
        ByteString.copyFrom(new byte[] {0x3a, 0x21});
    public static final int EVM_ADDRESS_SIZE = 20;
    public static final int ECDSA_SECP256K1_ALIAS_SIZE = 35;

    protected final Address address;
    protected ByteString alias = ByteString.EMPTY;

    public HederaEvmAccount(Address address) {
        this.address = address;
    }

    public ByteString getAlias() {
        return alias;
    }

    public void setAlias(final ByteString alias) {
        this.alias = alias;
    }

    public Address canonicalAddress() {
        if (alias.isEmpty()) {
            return address;
        } else {
            if (alias.size() == EVM_ADDRESS_SIZE) {
                return Address.wrap(Bytes.wrap(alias.toByteArray()));
            } else if (alias.size() == ECDSA_SECP256K1_ALIAS_SIZE
                && alias.startsWith(ECDSA_KEY_ALIAS_PREFIX)) {
                var addressBytes =
                    EthTxSigs.recoverAddressFromPubKey(alias.substring(2).toByteArray());
                return addressBytes == null
                    ? address
                    : Address.wrap(Bytes.wrap(addressBytes));
            } else {
                return address;
            }
        }
    }
}
