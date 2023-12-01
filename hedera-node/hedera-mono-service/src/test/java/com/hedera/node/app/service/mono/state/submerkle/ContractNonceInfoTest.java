/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.state.submerkle.ContractNonceInfo.MISSING_CONTRACT_NONCE_INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.hederahashgraph.api.proto.java.ContractID;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContractNonceInfoTest {
    private static final long shard = 1L;
    private static final long realm = 2L;
    private static final long num = 3L;

    private final EntityId contractId = new EntityId(shard, realm, num);
    private static final long nonce = 1L;

    private static ContractID contractID = ContractID.newBuilder()
            .setShardNum(shard)
            .setRealmNum(realm)
            .setContractNum(num)
            .build();
    private static final com.hederahashgraph.api.proto.java.ContractNonceInfo contractNonceInfoGrpc =
            com.hederahashgraph.api.proto.java.ContractNonceInfo.newBuilder()
                    .setContractId(contractID)
                    .setNonce(nonce)
                    .build();

    private ContractNonceInfo subject;

    @BeforeEach
    void setUp() {
        subject = new ContractNonceInfo(contractId, nonce);
    }

    @Test
    void serializableDetWorks() {
        assertEquals(ContractNonceInfo.MERKLE_VERSION, subject.getVersion());
        assertEquals(ContractNonceInfo.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    void deserializeWorks() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        final var newSubject = new ContractNonceInfo();

        given(in.readBoolean()).willReturn(true);
        given(in.readSerializable()).willReturn(contractId);
        given(in.readLong()).willReturn(nonce);

        newSubject.deserialize(in, ContractNonceInfo.MERKLE_VERSION);

        assertNotNull(subject);
        assertEquals(subject, newSubject);
    }

    @Test
    void serializeWorks() throws IOException {
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject.serialize(out);

        inOrder.verify(out).writeSerializable(contractId, true);
        inOrder.verify(out).writeLong(nonce);
    }

    @Test
    void gettersContractIdAndNonceWork() {
        assertEquals(contractId, subject.getContractId());
        assertEquals(nonce, subject.getNonce());
    }

    @Test
    void viewsWork() {
        assertEquals(contractNonceInfoGrpc, subject.toGrpc());
    }

    @Test
    void factoriesWork() {
        assertEquals(MISSING_CONTRACT_NONCE_INFO, ContractNonceInfo.fromGrpcEntityIdAndNonce(null, 0L));
        assertEquals(subject, ContractNonceInfo.fromGrpcEntityIdAndNonce(contractId, nonce));
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "ContractNonceInfo{contractId=EntityId{shard=" + shard + ", realm=" + realm + ", num=" + num
                        + "}, nonce=1}",
                subject.toString());
    }

    @Test
    void hashCodeAndEqualsWork() {
        final var subject = new ContractNonceInfo(contractId, nonce);
        final var subject2 = new ContractNonceInfo(contractId, nonce);
        final var subject3 = new EntityId(shard, realm, num);

        assertEquals(subject, subject2);
        assertEquals(subject.hashCode(), subject2.hashCode());
        assertNotEquals(subject, subject3);
        assertNotEquals(null, subject);
    }
}
