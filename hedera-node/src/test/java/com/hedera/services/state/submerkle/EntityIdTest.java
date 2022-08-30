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
package com.hedera.services.state.submerkle;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.utils.EntityIdUtils.tokenIdFromEvmAddress;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.inOrder;

import com.google.common.primitives.Longs;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.store.models.Id;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityIdTest {
    private static final long shard = 1L;
    private static final long realm = 2L;
    private static final long num = 3L;

    private static final FileID fileId =
            FileID.newBuilder().setShardNum(shard).setRealmNum(realm).setFileNum(num).build();
    private static final AccountID accountId =
            AccountID.newBuilder().setShardNum(shard).setRealmNum(realm).setAccountNum(num).build();
    private static final ContractID contractId =
            ContractID.newBuilder()
                    .setShardNum(shard)
                    .setRealmNum(realm)
                    .setContractNum(num)
                    .build();
    private static final TopicID topicId =
            TopicID.newBuilder().setShardNum(shard).setRealmNum(realm).setTopicNum(num).build();
    private static final TokenID tokenId =
            TokenID.newBuilder().setShardNum(shard).setRealmNum(realm).setTokenNum(num).build();
    private static final ScheduleID scheduleId =
            ScheduleID.newBuilder()
                    .setShardNum(shard)
                    .setRealmNum(realm)
                    .setScheduleNum(num)
                    .build();

    private EntityId subject;

    @BeforeEach
    void setup() {
        subject = new EntityId(shard, realm, num);
    }

    @Test
    void fromIdentityCodeWorks() {
        final var expected = new EntityId(0, 0, BitPackUtils.MAX_NUM_ALLOWED);

        final var actual = EntityId.fromIdentityCode((int) BitPackUtils.MAX_NUM_ALLOWED);

        assertEquals(expected, actual);
    }

    @Test
    void useEvmAddressDirectlyIfMirror() {
        final byte[] mockAddr = unhex("000000000000000000000000000000000cdefbbb");
        final var typedAddress = Address.wrap(Bytes.wrap(mockAddr));
        final var expected = EntityId.fromGrpcTokenId(tokenIdFromEvmAddress(typedAddress));
        final var actual = EntityId.fromAddress(typedAddress);
        assertEquals(expected, actual);
    }

    @Test
    void toAddressWorks() {
        final byte[] in = unhex("000000000000000000000000000000000cdefbbb");
        final var inter = new EntityId(0, 0, Longs.fromByteArray(Arrays.copyOfRange(in, 12, 20)));
        final byte[] out = inter.toEvmAddress().toArrayUnsafe();
        assertArrayEquals(in, out);
    }

    @Test
    void objectContractWorks() {
        final var one = subject;
        final var two = MISSING_ENTITY_ID;
        final var three = subject.copy();

        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertNotEquals(two, one);
        assertEquals(one, three);

        assertEquals(one.hashCode(), three.hashCode());
        assertNotEquals(one.hashCode(), two.hashCode());
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "EntityId{shard=" + shard + ", realm=" + realm + ", num=" + num + "}",
                subject.toString());
    }

    @Test
    void copyWorks() {
        final var copySubject = subject.copy();

        assertNotSame(subject, copySubject);
        assertEquals(subject, copySubject);
    }

    @Test
    void gettersWork() {
        assertEquals(shard, subject.shard());
        assertEquals(realm, subject.realm());
        assertEquals(num, subject.num());
    }

    @Test
    void settersWork() {
        subject.setNum(123);

        assertEquals(123, subject.num());
    }

    @Test
    void identityCodeWorks() {
        assertEquals((int) num, subject.identityCode());
    }

    @Test
    void factoriesWork() {
        assertEquals(MISSING_ENTITY_ID, EntityId.fromGrpcAccountId(null));
        assertEquals(MISSING_ENTITY_ID, EntityId.fromGrpcContractId(null));
        assertEquals(MISSING_ENTITY_ID, EntityId.fromGrpcTopicId(null));
        assertEquals(MISSING_ENTITY_ID, EntityId.fromGrpcFileId(null));
        assertEquals(MISSING_ENTITY_ID, EntityId.fromGrpcTokenId(null));
        assertEquals(MISSING_ENTITY_ID, EntityId.fromGrpcScheduleId(null));

        assertEquals(subject, EntityId.fromGrpcAccountId(accountId));
        assertEquals(subject, EntityId.fromGrpcContractId(contractId));
        assertEquals(subject, EntityId.fromGrpcTopicId(topicId));
        assertEquals(subject, EntityId.fromGrpcFileId(fileId));
        assertEquals(subject, EntityId.fromGrpcTokenId(tokenId));
        assertEquals(subject, EntityId.fromGrpcScheduleId(scheduleId));
    }

    @Test
    void idConstructorWorks() {
        Id id = IdUtils.asModelId("1.2.3");

        var subject = new EntityId(id);

        assertEquals(id.shard(), subject.shard());
        assertEquals(id.realm(), subject.realm());
        assertEquals(id.num(), subject.num());
        assertTrue(subject.matches(id));
    }

    @Test
    void serializableDetWorks() {
        assertEquals(EntityId.MERKLE_VERSION, subject.getVersion());
        assertEquals(EntityId.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    void deserializeWorks() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        final var newSubject = new EntityId();
        given(in.readLong()).willReturn(shard).willReturn(realm).willReturn(num);

        newSubject.deserialize(in, EntityId.MERKLE_VERSION);

        assertEquals(subject, newSubject);
    }

    @Test
    void serializeWorks() throws IOException {
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject.serialize(out);

        inOrder.verify(out).writeLong(shard);
        inOrder.verify(out).writeLong(realm);
        inOrder.verify(out).writeLong(num);
    }

    @Test
    void viewsWork() {
        assertEquals(accountId, subject.toGrpcAccountId());
        assertEquals(contractId, subject.toGrpcContractId());
        assertEquals(tokenId, subject.toGrpcTokenId());
        assertEquals(scheduleId, subject.toGrpcScheduleId());
    }

    @Test
    void matcherWorks() {
        final var diffShard = IdUtils.asAccount("2.2.3");
        final var diffRealm = IdUtils.asAccount("1.3.3");
        final var diffNum = IdUtils.asAccount("1.2.4");

        assertTrue(subject.matches(subject.toGrpcAccountId()));
        assertFalse(subject.matches(diffShard));
        assertFalse(subject.matches(diffRealm));
        assertFalse(subject.matches(diffNum));
    }

    @Test
    void fromNumWorks() {
        EntityId entityId = EntityId.fromNum(123L);
        assertEquals(123L, entityId.num());
        // NOTE: These values will change if the default static properties ever gets changed for the
        // test.
        assertEquals(0, entityId.realm());
        assertEquals(0, entityId.shard());
    }
}
