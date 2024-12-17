/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.statedumpers.legacy;

import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;

/**
 * Maps to proto Key of type contractID.
 *
 * <p>Just as a public-private key pair is used to control permissions for a Hedera entity (e.g., an
 * account), a {@code contractID} key is <i>also</i> used to control permissions for an entity. The
 * difference is that a {@code contractID} key requires a particular contract to be executing in
 * order to count as having "signed".
 *
 * <p>For example, suppose {@code 0.0.X} is an account. Its key is {@code Key{contractID=0.0.C}}.
 *
 * <p>Then <b>if</b> we are executing a contract operation, and the EVM receiver address in the
 * current frame is {@code 0.0.C}, then this EVM transaction can use system contracts to, for
 * example, transfer all the hbar from account {@code 0.0.X}.
 */
public class JContractIDKey extends JKey {
    private final long shardNum;
    private final long realmNum;
    private final long contractNum;

    public JContractIDKey(final ContractID contractID) {
        this.shardNum = contractID.getShardNum();
        this.realmNum = contractID.getRealmNum();
        this.contractNum = contractID.getContractNum();
    }

    public JContractIDKey(final long shardNum, final long realmNum, final long contractNum) {
        this.shardNum = shardNum;
        this.realmNum = realmNum;
        this.contractNum = contractNum;
    }

    @Override
    public JContractIDKey getContractIDKey() {
        return this;
    }

    @Override
    public boolean hasContractID() {
        return true;
    }

    public ContractID getContractID() {
        return ContractID.newBuilder()
                .setShardNum(shardNum)
                .setRealmNum(realmNum)
                .setContractNum(contractNum)
                .build();
    }

    public long getShardNum() {
        return shardNum;
    }

    public long getRealmNum() {
        return realmNum;
    }

    public long getContractNum() {
        return contractNum;
    }

    @Override
    public String toString() {
        return "<JContractID: " + shardNum + "." + realmNum + "." + contractNum + ">";
    }

    @Override
    public boolean isEmpty() {
        return (0 == contractNum);
    }

    @Override
    public boolean isValid() {
        return !isEmpty();
    }

    @Override
    protected Key convertJKeyEmpty() {
        return Key.newBuilder().setContractID(getContractID()).build();
    }
}
