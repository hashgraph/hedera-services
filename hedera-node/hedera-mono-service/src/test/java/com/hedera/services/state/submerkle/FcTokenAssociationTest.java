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
package com.hedera.services.state.submerkle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FcTokenAssociationTest {
    private final long accountNum = 6;
    private final long tokenNum = 3;
    private final FcTokenAssociation subject = new FcTokenAssociation(tokenNum, accountNum);
    private final TokenAssociation grpc =
            TokenAssociation.newBuilder()
                    .setTokenId(TokenID.newBuilder().setTokenNum(tokenNum).build())
                    .setAccountId(AccountID.newBuilder().setAccountNum(accountNum).build())
                    .build();

    @Test
    void serializationWorks() throws IOException, ConstructableRegistryException {
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(EntityId.class, EntityId::new));

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);

        subject.serialize(dos);
        dos.flush();

        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        final var newSubject = new FcTokenAssociation();
        newSubject.deserialize(din, FcTokenAssociation.RELEASE_0180_VERSION);

        assertEquals(subject, newSubject);
    }

    @Test
    void toStringWorks() {
        assertEquals("FcTokenAssociation{token=3, account=6}", subject.toString());
    }

    @Test
    void toGrpcWorks() {
        assertEquals(grpc, subject.toGrpc());
    }

    @Test
    void fromGrpcWorks() {
        final var newSubject = FcTokenAssociation.fromGrpc(grpc);
        assertEquals(subject, newSubject);
        assertEquals(subject.token(), newSubject.token());
        assertEquals(subject.account(), newSubject.account());
    }

    @Test
    void hashCodeWorks() {
        assertDoesNotThrow(subject::hashCode);
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(FcTokenAssociation.CURRENT_VERSION, subject.getVersion());
        assertEquals(FcTokenAssociation.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }
}
