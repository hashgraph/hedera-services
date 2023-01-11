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
package com.hedera.node.app.service.evm.store.models;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
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

    public void setAlias(final ByteString alias) {
        this.alias = alias;
    }

    public ByteString getAlias() {
        return alias;
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
                        EthSigsUtils.recoverAddressFromPubKey(alias.substring(2).toByteArray());
                return addressBytes.length == 0 ? address : Address.wrap(Bytes.wrap(addressBytes));
            } else {
                return address;
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(HederaEvmAccount.class)
                .add("address", address.toHexString())
                .add("alias", getAlias().toStringUtf8())
                .toString();
    }
}
