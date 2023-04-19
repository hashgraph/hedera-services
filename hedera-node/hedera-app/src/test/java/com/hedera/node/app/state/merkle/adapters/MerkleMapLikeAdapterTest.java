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

package com.hedera.node.app.state.merkle.adapters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerklePayerRecords;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.node.app.state.merkle.memory.InMemoryKey;
import com.hedera.node.app.state.merkle.memory.InMemoryValue;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Set;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerkleMapLikeAdapterTest {
    private static final EntityNum A_NUM = EntityNum.fromInt(1234);
    private static final EntityNum B_NUM = EntityNum.fromInt(2345);
    private static final EntityNum C_NUM = EntityNum.fromInt(3456);
    private static final EntityNum Z_NUM = EntityNum.fromInt(7890);
    private static final ExpirableTxnRecord A_RECORD =
            new ExpirableTxnRecord(ExpirableTxnRecord.newBuilder().setAlias(ByteString.copyFromUtf8("alpha")));
    private static final ExpirableTxnRecord B_RECORD =
            new ExpirableTxnRecord(ExpirableTxnRecord.newBuilder().setAlias(ByteString.copyFromUtf8("bravo")));
    private static final ExpirableTxnRecord C_RECORD =
            new ExpirableTxnRecord(ExpirableTxnRecord.newBuilder().setAlias(ByteString.copyFromUtf8("charlie")));
    private static final ExpirableTxnRecord Z_RECORD =
            new ExpirableTxnRecord(ExpirableTxnRecord.newBuilder().setAlias(ByteString.copyFromUtf8("zulu")));
    private final MerklePayerRecords A_RECORDS = new MerklePayerRecords();
    private final MerklePayerRecords B_RECORDS = new MerklePayerRecords();
    private final MerklePayerRecords C_RECORDS = new MerklePayerRecords();
    private final MerklePayerRecords Z_RECORDS = new MerklePayerRecords();

    {
        A_RECORDS.offer(A_RECORD);
        B_RECORDS.offer(B_RECORD);
        C_RECORDS.offer(C_RECORD);
        Z_RECORDS.offer(Z_RECORD);
    }

    @Mock
    private SchemaRegistry schemaRegistry;

    private final MerkleMap<InMemoryKey<EntityNum>, InMemoryValue<EntityNum, MerklePayerRecords>> real =
            new MerkleMap<>();

    private StateMetadata<EntityNum, MerklePayerRecords> metadata;
    private MerkleMapLike<EntityNum, MerklePayerRecords> subject;

    @Test
    void forEachNodeAndSizeAndKeySetDelegate() {
        final BiConsumer<EntityNum, MerklePayerRecords> consumer = mock(BiConsumer.class);

        setupSubjectAdaptingReal();

        putToReal(A_NUM, A_RECORDS);
        putToReal(B_NUM, B_RECORDS);
        putToReal(C_NUM, C_RECORDS);

        subject.forEachNode(consumer);
        assertEquals(3, subject.size());
        assertEquals(Set.of(A_NUM, B_NUM, C_NUM), subject.keySet());

        verify(consumer).accept(A_NUM, A_RECORDS);
        verify(consumer).accept(B_NUM, B_RECORDS);
        verify(consumer).accept(C_NUM, C_RECORDS);
    }

    @Test
    void forEachDelegate() {
        final BiConsumer<EntityNum, MerklePayerRecords> consumer = mock(BiConsumer.class);

        setupSubjectAdaptingReal();

        putToReal(A_NUM, A_RECORDS);
        putToReal(B_NUM, B_RECORDS);
        putToReal(C_NUM, C_RECORDS);

        subject.forEach(consumer);

        verify(consumer).accept(A_NUM, A_RECORDS);
        verify(consumer).accept(B_NUM, B_RECORDS);
        verify(consumer).accept(C_NUM, C_RECORDS);
    }

    @Test
    void containsKeyDelegates() {
        setupSubjectAdaptingReal();

        putToReal(A_NUM, A_RECORDS);

        assertTrue(subject.containsKey(A_NUM));
        assertFalse(subject.containsKey(B_NUM));
    }

    @Test
    void isEmptyDelegates() {
        setupSubjectAdaptingReal();

        assertTrue(subject.isEmpty());
    }

    @Test
    void getHashDelegates() {
        setupSubjectAdaptingReal();

        putToReal(A_NUM, A_RECORDS);

        assertSame(real.getHash(), subject.getHash());
    }

    @Test
    void removeDelegates() {
        setupSubjectAdaptingReal();

        putToReal(A_NUM, A_RECORDS);

        assertSame(A_RECORDS, subject.remove(A_NUM));
        assertNull(subject.remove(B_NUM));
    }

    @Test
    void getAndPutDelegate() {
        setupSubjectAdaptingReal();

        putToReal(A_NUM, A_RECORDS);

        assertSame(A_RECORDS, subject.get(A_NUM));
        assertNotNull(subject.put(A_NUM, A_RECORDS));
        assertNull(subject.get(Z_NUM));
        final var shouldBeDefault = subject.getOrDefault(Z_NUM, Z_RECORDS);
        assertSame(Z_RECORDS, shouldBeDefault);
    }

    @Test
    void getForModifyDelegates() {
        setupSubjectAdaptingReal();

        putToReal(A_NUM, A_RECORDS);

        final var modifiable = subject.getForModify(A_NUM);
        modifiable.offer(B_RECORD);

        final var wrappedValue = real.get(new InMemoryKey<>(A_NUM));
        final var numRecords = wrappedValue.getValue().mutableQueue().size();
        assertEquals(2, numRecords);
    }

    @Test
    void putDelegates() {
        setupSubjectAdaptingReal();

        final var key = EntityNum.fromInt(1234);
        final var value = new MerklePayerRecords();
        value.offer(new ExpirableTxnRecord());

        subject.put(key, value);

        final var wrappedValue = real.get(new InMemoryKey<>(key));
        assertNotNull(wrappedValue);
        assertEquals(value, wrappedValue.getValue());
    }

    private void setupSubjectAdaptingReal() {
        final var tokenService = new TokenServiceImpl();
        final ArgumentCaptor<Schema> schemaCaptor = ArgumentCaptor.forClass(Schema.class);

        tokenService.registerSchemas(schemaRegistry);

        verify(schemaRegistry).register(schemaCaptor.capture());
        final var schema = schemaCaptor.getValue();

        final var payerRecordsDef = schema.statesToCreate().stream()
                .filter(def -> def.stateKey().equals(TokenServiceImpl.PAYER_RECORDS_KEY))
                .findFirst()
                .orElseThrow();

        metadata = new StateMetadata<>(TokenService.NAME, schema, payerRecordsDef);

        subject = MerkleMapLikeAdapter.unwrapping(metadata, real);
    }

    private void putToReal(final EntityNum num, final MerklePayerRecords records) {
        real.put(new InMemoryKey<>(num), new InMemoryValue<>(metadata, new InMemoryKey<>(num), records));
    }
}
