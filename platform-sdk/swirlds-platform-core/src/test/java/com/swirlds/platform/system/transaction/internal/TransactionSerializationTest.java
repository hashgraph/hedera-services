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

package com.swirlds.platform.system.transaction.internal;

import static com.swirlds.common.io.streams.SerializableDataOutputStream.getInstanceSerializedLength;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHashBytes;
import static com.swirlds.common.test.fixtures.RandomUtils.randomSignatureBytes;
import static com.swirlds.common.test.fixtures.io.SerializationUtils.serializeDeserialize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.platform.event.StateSignaturePayload;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SerializableWithKnownLength;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionSerializationTest {
    Random random = new Random();

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.platform.system.transaction");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 10, 64})
    void SignatureSerializeDeserializeTest(final int sigSize) throws IOException {
        byte[] nbyte = null;
        if (sigSize > 0) {
            nbyte = new byte[sigSize];
            random.nextBytes(nbyte);
        }
        final Bytes signature = randomSignatureBytes(random);
        final Bytes stateHash = randomHashBytes(random);
        final StateSignatureTransaction systemTransactionSignature =
                new StateSignatureTransaction(StateSignaturePayload.newBuilder()
                        .round(random.nextLong())
                        .signature(signature)
                        .hash(stateHash)
                        .build());
        final StateSignatureTransaction deserialized = serializeDeserialize(systemTransactionSignature);

        assertEquals(systemTransactionSignature.getPayload(), deserialized.getPayload());
        assertEquals(systemTransactionSignature.isSystem(), deserialized.isSystem());
        assertEquals(systemTransactionSignature.getVersion(), deserialized.getVersion());
        assertEquals(systemTransactionSignature.getClassId(), deserialized.getClassId());
        assertEquals(
                systemTransactionSignature.getMinimumSupportedVersion(), deserialized.getMinimumSupportedVersion());

        assertEquals(systemTransactionSignature, deserialized);

        TestExpectedSerializationLength(systemTransactionSignature, true);
        TestExpectedSerializationLength(deserialized, true);

        TestExpectedSerializationLength(systemTransactionSignature, false);
        TestExpectedSerializationLength(deserialized, false);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 64})
    void ApplicationWithoutSignatures(final int contentSize) throws IOException {
        byte[] nbyte = null;
        if (contentSize > 0) {
            nbyte = new byte[contentSize];
            random.nextBytes(nbyte);
        }

        final SwirldTransaction applicationTransaction;
        if (contentSize == 0) {
            // should throw NPE error
            try {
                applicationTransaction = new SwirldTransaction(nbyte);
            } catch (final NullPointerException e) {
                assertEquals("contents", e.getMessage());
                return;
            }
        } else {
            applicationTransaction = new SwirldTransaction(nbyte);
        }

        final SwirldTransaction deserialized = serializeDeserialize(applicationTransaction);
        assertEquals(applicationTransaction, deserialized);

        TestExpectedSerializationLength(applicationTransaction, true);
        TestExpectedSerializationLength(deserialized, true);

        TestExpectedSerializationLength(applicationTransaction, false);
        TestExpectedSerializationLength(deserialized, false);
    }

    static void TestExpectedSerializationLength(
            final SerializableWithKnownLength transaction, final boolean writeClassId) throws IOException {
        try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            try (final SerializableDataOutputStream dos = new SerializableDataOutputStream(bos)) {
                dos.writeSerializable(transaction, writeClassId);
                assertEquals(dos.size(), getInstanceSerializedLength(transaction, true, writeClassId));
            }
        }
    }

    //  getSerializedLength(  SerializableDataOutputStream
}
