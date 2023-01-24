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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.hyperledger.besu.datatypes.Address;

public class EvmNftInfo {

    private long serialNumber;
    private Address account;
    private long creationTime;
    private byte[] metadata;
    private Address spender;

    public EvmNftInfo() {}

    public EvmNftInfo(
            long serialNumber,
            @NonNull Address account,
            long creationTime,
            byte[] metadata,
            Address spender) {
        this.serialNumber = serialNumber;
        this.account = Objects.requireNonNull(account);
        this.creationTime = creationTime;
        this.metadata = metadata;
        this.spender = spender;
    }

    public long getSerialNumber() {
        return serialNumber;
    }

    public Address getAccount() {
        return account;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public byte[] getMetadata() {
        return metadata;
    }

    public Address getSpender() {
        return spender != null ? spender : Address.ZERO;
    }
}
