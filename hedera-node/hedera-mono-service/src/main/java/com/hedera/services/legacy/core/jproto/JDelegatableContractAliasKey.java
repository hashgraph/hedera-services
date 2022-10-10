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

import static com.swirlds.common.utility.CommonUtils.hex;

import com.hederahashgraph.api.proto.java.ContractID;

public class JDelegatableContractAliasKey extends JContractAliasKey {
    public JDelegatableContractAliasKey(final ContractID contractID) {
        super(contractID);
    }

    public JDelegatableContractAliasKey(
            final long shard, final long realm, final byte[] evmAddress) {
        super(shard, realm, evmAddress);
    }

    @Override
    public JDelegatableContractAliasKey getDelegatableContractAliasKey() {
        return this;
    }

    @Override
    public boolean hasDelegatableContractAlias() {
        return true;
    }

    @Override
    public boolean hasContractAlias() {
        return false;
    }

    @Override
    public String toString() {
        return "<JDelegatableContractAlias: "
                + getShardNum()
                + "."
                + getRealmNum()
                + "."
                + hex(getEvmAddress())
                + ">";
    }
}
