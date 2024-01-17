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

package com.hedera.node.app.service.mono.state.submerkle;

import static com.hedera.node.app.service.mono.state.serdes.IoUtils.readNullableSerializable;
import static com.hedera.node.app.service.mono.state.serdes.IoUtils.writeNullableSerializable;

import com.google.common.base.MoreObjects;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Objects;

public class ContractNonceInfo implements SelfSerializable {

    static final int MERKLE_VERSION = 1;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0x18ec32eaf9371551L;
    public static final ContractNonceInfo MISSING_CONTRACT_NONCE_INFO = new ContractNonceInfo(null, 0);

    private EntityId contractId;
    private long nonce;

    public ContractNonceInfo() {}

    public ContractNonceInfo(final EntityId contractId, final long nonce) {
        this.contractId = contractId;
        this.nonce = nonce;
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        contractId = readNullableSerializable(in);
        nonce = in.readLong();
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        writeNullableSerializable(contractId, out);
        out.writeLong(nonce);
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return MERKLE_VERSION;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("contractId", contractId)
                .add("nonce", nonce)
                .toString();
    }

    public EntityId getContractId() {
        return contractId;
    }

    public long getNonce() {
        return nonce;
    }

    public static ContractNonceInfo fromGrpcEntityIdAndNonce(final EntityId contractId, final long nonce) {
        if (contractId == null) {
            return MISSING_CONTRACT_NONCE_INFO;
        }
        return new ContractNonceInfo(contractId, nonce);
    }

    public com.hederahashgraph.api.proto.java.ContractNonceInfo toGrpc() {
        final var grpc = com.hederahashgraph.api.proto.java.ContractNonceInfo.newBuilder();
        if (contractId != null) {
            grpc.setContractId(contractId.toGrpcContractId());
        }
        grpc.setNonce(nonce);
        return grpc.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || ContractNonceInfo.class != o.getClass()) {
            return false;
        }
        final ContractNonceInfo that = (ContractNonceInfo) o;
        return contractId.equals(that.contractId) && nonce == that.nonce;
    }

    @Override
    public int hashCode() {
        return Objects.hash(contractId, nonce);
    }
}
