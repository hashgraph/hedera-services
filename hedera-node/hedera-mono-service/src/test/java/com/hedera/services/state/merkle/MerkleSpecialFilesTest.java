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
package com.hedera.services.state.merkle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.google.common.primitives.Longs;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.state.merkle.internals.BytesElement;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.fcqueue.FCQueue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerkleSpecialFilesTest {
    private static final byte[] stuff =
            "01234578901234578901234578901234578901234567".getBytes(StandardCharsets.UTF_8);
    private static final FileID fid = IdUtils.asFile("0.0.150");
    private static final FileID secondFid = IdUtils.asFile("0.0.151");
    private static final byte[] stuffHash = CommonUtils.noThrowSha384HashOf(stuff);

    private MerkleSpecialFiles subject;

    @BeforeEach
    void setUp() {
        subject = new MerkleSpecialFiles();
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(0x1608d4b49c28983aL, subject.getClassId(), "Unexpected class id");
        assertEquals(2, subject.getVersion(), "Unexpected version");
    }

    @Test
    void isSelfHashing() {
        assertTrue(subject.isSelfHashing());
    }

    @Test
    void emptySpecialFilesNeverMatchHashes() {
        assertFalse(
                subject.hashMatches(fid, stuffHash), "Empty special files shouldn't match hashes");
    }

    @Test
    void copyCreatesNewMaps() {
        subject.append(fid, stuff);
        final var copySub = subject.copy();

        assertNotSame(
                subject.getFileContents(),
                copySub.getFileContents(),
                "copy() should create new file contents map");
        for (var entry : subject.getFileContents().entrySet()) {
            assertNotSame(entry.getValue(), copySub.getFileContents().get(entry.getKey()));
        }
    }

    @Test
    void becomesImmutableOnCopy() {
        final var stuff = "asdf".getBytes(StandardCharsets.UTF_8);

        subject.copy();

        assertThrows(
                MutabilityException.class,
                () -> subject.update(fid, stuff),
                "Copies shouldn't be updatable");
        assertThrows(
                MutabilityException.class,
                () -> subject.append(fid, stuff),
                "Copies shouldn't be appendable");
    }

    @Test
    void updateClearsHashCache() {
        subject.update(fid, stuff);
        assertTrue(subject.hashMatches(fid, stuffHash), "Updated stuff should have SHA-384 hash");
        subject.update(fid, stuffHash);
        assertFalse(subject.getHashCache().containsKey(fid));
    }

    @Test
    void appendClearsHashCache() {
        subject.update(fid, stuff);
        assertTrue(subject.hashMatches(fid, stuffHash), "Updated stuff should have SHA-384 hash");
        subject.append(fid, stuffHash);
        assertFalse(subject.getHashCache().containsKey(fid));
    }

    @Test
    void updateAccomplishesTheExpected() {
        subject.update(fid, stuff);

        assertTrue(subject.contains(fid), "Updating should create file if not present");

        assertArrayEquals(stuff, subject.get(fid), "Updated stuff should be identical");

        assertTrue(subject.hashMatches(fid, stuffHash), "Updated stuff should have SHA-384 hash");

        assertArrayEquals(new byte[0], subject.get(secondFid));
    }

    @Test
    void creatingAppendAccomplishesTheExpected() {
        subject.append(secondFid, stuff);

        assertTrue(subject.contains(secondFid), "Appending should create file if not present");

        assertArrayEquals(stuff, subject.get(secondFid), "Appended stuff should be identical");

        assertTrue(
                subject.hashMatches(secondFid, stuffHash),
                "Appended stuff should have SHA-384 hash");
    }

    @Test
    void multiAppendAccomplishesTheExpected() {
        subject.append(secondFid, Arrays.copyOfRange(stuff, 0, stuff.length / 2));
        subject.append(secondFid, Arrays.copyOfRange(stuff, stuff.length / 2, stuff.length));

        assertTrue(subject.contains(secondFid), "Appending should create file if not present");

        assertArrayEquals(stuff, subject.get(secondFid), "Appended stuff should be identical");

        assertTrue(
                subject.hashMatches(secondFid, stuffHash),
                "Appended stuff should have SHA-384 hash");
    }

    @Test
    void hashSummarizesAsExpected() throws IOException {
        subject.append(fid, Arrays.copyOfRange(stuff, 0, stuff.length / 2));
        subject.append(secondFid, Arrays.copyOfRange(stuff, stuff.length / 2, stuff.length));

        final var fcq = new FCQueue<BytesElement>();
        fcq.add(new BytesElement(subject.get(fid)));
        final var secondFcq = new FCQueue<BytesElement>();
        secondFcq.add(new BytesElement(subject.get(secondFid)));

        final var baos = new ByteArrayOutputStream();
        baos.write(Longs.toByteArray(fid.getFileNum()));
        baos.write(fcq.getHash().getValue());
        baos.write(Longs.toByteArray(secondFid.getFileNum()));
        baos.write(secondFcq.getHash().getValue());
        final var expected = CommonUtils.noThrowSha384HashOf(baos.toByteArray());

        assertArrayEquals(expected, subject.getHash().getValue());
    }

    @Test
    void propagatesFailureOnGetThatShouldBeNeverProblematic() throws IOException {
        @SuppressWarnings("unchecked")
        final Supplier<ByteArrayOutputStream> baosSupplier =
                (Supplier<ByteArrayOutputStream>) mock(Supplier.class);

        final var baos = mock(ByteArrayOutputStream.class);
        willThrow(IOException.class).given(baos).write(stuff);
        given(baosSupplier.get()).willReturn(baos);

        MerkleSpecialFiles.setBaosSupplier(baosSupplier);

        subject.append(secondFid, stuff);
        assertThrows(UncheckedIOException.class, () -> subject.get(secondFid));

        MerkleSpecialFiles.setBaosSupplier(ByteArrayOutputStream::new);
    }

    @Test
    void propagatesFailureOnHashThatShouldBeNeverProblematic() throws IOException {
        @SuppressWarnings("unchecked")
        final Supplier<ByteArrayOutputStream> baosSupplier =
                (Supplier<ByteArrayOutputStream>) mock(Supplier.class);

        final var baos = mock(ByteArrayOutputStream.class);
        willThrow(IOException.class).given(baos).write(Longs.toByteArray(secondFid.getFileNum()));
        given(baosSupplier.get()).willReturn(baos);

        MerkleSpecialFiles.setBaosSupplier(baosSupplier);

        subject.append(secondFid, stuff);
        assertThrows(UncheckedIOException.class, () -> subject.getHash());

        MerkleSpecialFiles.setBaosSupplier(ByteArrayOutputStream::new);
    }

    @Test
    void canDeserializeMemcopyVersion() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        given(in.readInt()).willReturn(2);
        given(in.readLong()).willReturn(fid.getFileNum()).willReturn(secondFid.getFileNum());
        given(in.readByteArray(Integer.MAX_VALUE)).willReturn(stuff);

        subject.deserialize(in, 1);

        assertTrue(subject.hashMatches(fid, stuffHash));
        assertTrue(subject.hashMatches(secondFid, stuffHash));
    }

    @Test
    void liveFireSerdeWorksWithNonEmpty() throws IOException, ConstructableRegistryException {
        final var baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(
                                MerkleSpecialFiles.class, MerkleSpecialFiles::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(FCQueue.class, FCQueue::new));
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(BytesElement.class, BytesElement::new));

        subject.update(fid, Arrays.copyOfRange(stuff, 0, stuff.length / 2));
        subject.update(secondFid, Arrays.copyOfRange(stuff, stuff.length / 2, stuff.length));

        subject.serialize(dos);
        dos.flush();
        final var bytes = baos.toByteArray();
        final var bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        final var newSubject = new MerkleSpecialFiles();
        newSubject.deserialize(din, MerkleSpecialFiles.CURRENT_VERSION);

        assertArrayEquals(
                subject.get(fid),
                newSubject.get(fid),
                "Deserialized contents should match for first file");
        assertArrayEquals(
                subject.get(secondFid),
                newSubject.get(secondFid),
                "Deserialized contents should match for second file");
    }

    @Test
    void liveFireSerdeWorksWithEmpty() throws IOException, ConstructableRegistryException {
        final var baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(
                                MerkleSpecialFiles.class, MerkleSpecialFiles::new));

        subject.serialize(dos);
        dos.flush();
        final var bytes = baos.toByteArray();
        final var bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        final var newSubject = new MerkleSpecialFiles();
        newSubject.deserialize(din, MerkleSpecialFiles.CURRENT_VERSION);

        assertTrue(newSubject.getFileContents().isEmpty(), "Deserialized instance should be empty");
    }

    @Test
    void checkHashCodesDiverse() {
        Set<Integer> hashCodes = new HashSet<>();
        hashCodes.add(subject.hashCode());
        subject.append(fid, "hello".getBytes());
        hashCodes.add(subject.hashCode());
        subject.append(secondFid, "hello".getBytes());
        hashCodes.add(subject.hashCode());
        subject.append(fid, " ".getBytes());
        hashCodes.add(subject.hashCode());
        assertTrue(hashCodes.size() >= 3);
    }

    @Test
    void checkStringContainsContents() {
        subject.append(fid, new byte[] {123});
        assertTrue(subject.toString().contains(String.valueOf(fid.getFileNum())));
        subject.append(secondFid, new byte[] {111});
        assertTrue(
                subject.toString().contains(String.valueOf(secondFid.getFileNum())),
                subject.toString());
    }

    @Test
    @SuppressWarnings("java:S3415")
    void checkEqualityWorks() {
        assertEquals(subject, subject);
        // Note: suppressed warning here because check needs to cover null code-path of equals
        // method.
        assertNotEquals(subject, null);
        assertNotEquals(subject, new Object());

        // Matching initial contents
        assertEquals(new MerkleSpecialFiles(), new MerkleSpecialFiles());
        // Matching added contents
        var msf1 = new MerkleSpecialFiles();
        var msf2 = new MerkleSpecialFiles();
        msf1.append(fid, "hello".getBytes());
        msf2.append(fid, "hello".getBytes());
        assertEquals(msf2, msf1);
        assertEquals(msf1, msf2);

        // Mismatching contents
        msf2.append(fid, " ".getBytes());
        assertNotEquals(msf2, msf1);
        assertNotEquals(msf1, msf2);
    }
}
