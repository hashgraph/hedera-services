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
package com.hedera.services.files.store;

import static com.hedera.services.files.store.FcBlobsBytesStore.getEntityNumFromPath;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobKey.Type;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FcBlobsBytesStoreTest {
    private static final byte[] aData = "BlobA".getBytes();
    private static final String dataPath = "/0/f112";
    private static final String metadataPath = "/0/k3";
    private static final String bytecodePath = "/0/s4";
    private static final String expiryTimePath = "/0/e5";
    private VirtualBlobKey pathAKey;

    private VirtualBlobValue blobA;
    private VirtualMap<VirtualBlobKey, VirtualBlobValue> pathedBlobs;

    private FcBlobsBytesStore subject;

    @BeforeEach
    void setup() {
        pathedBlobs = mock(VirtualMap.class);

        givenMockBlobs();
        subject = new FcBlobsBytesStore(() -> pathedBlobs);

        pathAKey = subject.at(dataPath);
    }

    @Test
    void delegatesClear() {
        assertThrows(UnsupportedOperationException.class, subject::clear);
    }

    @Test
    void delegatesRemoveOfMissing() {
        assertNull(subject.remove(dataPath));

        verify(pathedBlobs).put(subject.at(dataPath), new VirtualBlobValue(new byte[0]));
    }

    @Test
    void delegatesRemoveAndReturnsNull() {
        given(pathedBlobs.remove(subject.at(dataPath))).willReturn(blobA);

        assertNull(subject.remove(dataPath));
    }

    @Test
    void delegatesPutUsingGetAndFactoryIfNewBlob() {
        final var keyCaptor = ArgumentCaptor.forClass(VirtualBlobKey.class);
        final var valueCaptor = ArgumentCaptor.forClass(VirtualBlobValue.class);

        final var oldBytes = subject.put(dataPath, aData);

        verify(pathedBlobs).put(keyCaptor.capture(), valueCaptor.capture());

        assertEquals(pathAKey, keyCaptor.getValue());
        assertSame(blobA.getData(), valueCaptor.getValue().getData());
        assertNull(oldBytes);
    }

    @Test
    void propagatesNullFromGet() {
        given(pathedBlobs.get(subject.at(dataPath))).willReturn(null);

        assertNull(subject.get(dataPath));
    }

    @Test
    void delegatesGet() {
        given(pathedBlobs.get(pathAKey)).willReturn(blobA);

        assertArrayEquals(aData, subject.get(dataPath));
    }

    @Test
    void delegatesContainsKey() {
        given(pathedBlobs.containsKey(pathAKey)).willReturn(true);

        assertTrue(subject.containsKey(dataPath));
    }

    @Test
    void delegatesIsEmpty() {
        given(pathedBlobs.isEmpty()).willReturn(true);

        assertTrue(subject.isEmpty());
        verify(pathedBlobs).isEmpty();
    }

    @Test
    void doesNotSupportSize() {
        assertThrows(UnsupportedOperationException.class, subject::size);
    }

    @Test
    void entrySetThrows() {
        assertThrows(UnsupportedOperationException.class, subject::entrySet);
    }

    private void givenMockBlobs() {
        blobA = mock(VirtualBlobValue.class);

        given(blobA.getData()).willReturn(aData);
    }

    @Test
    void validateBlobKeyBasedOnPath() {
        assertEquals(new VirtualBlobKey(Type.FILE_DATA, 112), subject.at(dataPath));
        assertEquals(new VirtualBlobKey(Type.FILE_METADATA, 3), subject.at(metadataPath));
        assertEquals(new VirtualBlobKey(Type.CONTRACT_BYTECODE, 4), subject.at(bytecodePath));
        assertEquals(
                new VirtualBlobKey(Type.SYSTEM_DELETED_ENTITY_EXPIRY, 5),
                subject.at(expiryTimePath));
    }

    @Test
    void validateEntityNumBasedOnPath() {
        assertEquals(112, getEntityNumFromPath(dataPath));
        assertEquals(3, getEntityNumFromPath(metadataPath));
        assertEquals(4, getEntityNumFromPath(bytecodePath));
        assertEquals(5, getEntityNumFromPath(expiryTimePath));
    }
}
