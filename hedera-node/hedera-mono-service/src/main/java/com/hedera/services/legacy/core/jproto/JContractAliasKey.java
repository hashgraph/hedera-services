/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.legacy.core.jproto;

import static com.hedera.services.utils.EntityIdUtils.EVM_ADDRESS_SIZE;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractID;
import com.swirlds.common.utility.CommonUtils;

public class JContractAliasKey extends JKey {
    private final long shardNum;
    private final long realmNum;
    private final byte[] evmAddress;

    public JContractAliasKey(final ContractID contractID) {
        this.shardNum = contractID.getShardNum();
        this.realmNum = contractID.getRealmNum();
        this.evmAddress = contractID.getEvmAddress().toByteArray();
    }

    public JContractAliasKey(final long shardNum, final long realmNum, final byte[] evmAddress) {
        this.shardNum = shardNum;
        this.realmNum = realmNum;
        this.evmAddress = evmAddress;
    }

    @Override
    public JContractAliasKey getContractAliasKey() {
        return this;
    }

    @Override
    public boolean hasContractAlias() {
        return true;
    }

    public ContractID getContractID() {
        return ContractID.newBuilder()
                .setShardNum(shardNum)
                .setRealmNum(realmNum)
                .setEvmAddress(ByteString.copyFrom(evmAddress))
                .build();
    }

    public long getShardNum() {
        return shardNum;
    }

    public long getRealmNum() {
        return realmNum;
    }

    public byte[] getEvmAddress() {
        return evmAddress;
    }

    @Override
    public String toString() {
        return "<JContractAlias: "
                + shardNum
                + "."
                + realmNum
                + "."
                + CommonUtils.hex(evmAddress)
                + ">";
    }

    @Override
    public boolean isEmpty() {
        return evmAddress.length == 0;
    }

    @Override
    public boolean isValid() {
        return !isEmpty() && evmAddress.length == EVM_ADDRESS_SIZE;
    }
}
