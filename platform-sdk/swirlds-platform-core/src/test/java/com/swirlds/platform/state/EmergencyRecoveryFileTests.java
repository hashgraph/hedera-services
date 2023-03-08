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

package com.swirlds.platform.state;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.RandomUtils;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class EmergencyRecoveryFileTests {

    private static final String FILENAME = "emergencyRecovery.yaml";
    public static final String FIELD_NAME_ROUND = "round";
    public static final String FIELD_NAME_HASH = "hash";
    public static final String FIELD_NAME_TIMESTAMP = "timestamp";

    @TempDir
    Path tmpDir;

    @Test
    void testReadWrite() throws IOException {
        final Random r = RandomUtils.getRandomPrintSeed();
        final EmergencyRecoveryFile toWrite = createRecoveryFile(r);
        toWrite.write(tmpDir);

        final EmergencyRecoveryFile readIn = EmergencyRecoveryFile.read(tmpDir);

        assertNotNull(readIn, "emergency round data should not be null");
        assertEquals(toWrite.round(), readIn.round(), "round does not match");
        assertEquals(toWrite.hash(), readIn.hash(), "hash does not match");
        assertEquals(toWrite.timestamp(), readIn.timestamp(), "state timestamp does not match");
    }

    @Test
    void testReadWriteWithBootstrap() throws IOException {
        final Random r = RandomUtils.getRandomPrintSeed();
        final EmergencyRecoveryFile toWrite = createRecoveryFileWithBootstrap(r);
        toWrite.write(tmpDir);

        final EmergencyRecoveryFile readIn = EmergencyRecoveryFile.read(tmpDir);

        assertNotNull(readIn, "emergency round data should not be null");
        assertEquals(toWrite.round(), readIn.round(), "round does not match");
        assertEquals(toWrite.hash(), readIn.hash(), "hash does not match");
        assertEquals(toWrite.timestamp(), readIn.timestamp(), "state timestamp does not match");
        assertNotNull(readIn.recovery().boostrap(), "bootstrap should not be null");
        assertEquals(toWrite.recovery().boostrap().timestamp(),
                readIn.recovery().boostrap().timestamp(), "bootstrap timestamp does not match");
    }

    @Test
    void testAdditionalValuesDontThrow() {
        final Random r = RandomUtils.getRandomPrintSeed();
        writeFile(
                Pair.of("some field", "some value"),
                Pair.of(FIELD_NAME_ROUND, randomLongString(r)),
                Pair.of(FIELD_NAME_HASH, randomHashString(r)),
                Pair.of(FIELD_NAME_TIMESTAMP, randomInstantString(r)),
                Pair.of("anotherOne", "anotherValue"));
        assertDoesNotThrow(() -> EmergencyRecoveryFile.read(tmpDir), "Additional fields should not cause problems");

        writeFileWithBootstrap(
                Pair.of("some field", "some value"),
                Pair.of(FIELD_NAME_ROUND, randomLongString(r)),
                Pair.of(FIELD_NAME_HASH, randomHashString(r)),
                Pair.of(FIELD_NAME_TIMESTAMP, randomInstantString(r)),
                Pair.of("anotherOne", "anotherValue"));
        assertDoesNotThrow(() -> EmergencyRecoveryFile.read(tmpDir), "Bootstrap field should not cause problems");
    }

    @Test
    void testReadFileWithFieldMissing() {
        final Random r = RandomUtils.getRandomPrintSeed();
        writeFileWithBootstrap(Pair.of(FIELD_NAME_ROUND, randomLongString(r)));
        assertThrows(
                IOException.class, () -> EmergencyRecoveryFile.read(tmpDir), "Reading an invalid file should throw");
        writeFile(Pair.of(FIELD_NAME_HASH, randomHashString(r)));
        assertThrows(
                IOException.class, () -> EmergencyRecoveryFile.read(tmpDir), "Reading an invalid file should throw");
        writeFile(Pair.of(FIELD_NAME_TIMESTAMP, randomInstantString(r)));
        assertThrows(
                IOException.class, () -> EmergencyRecoveryFile.read(tmpDir), "Reading an invalid file should throw");
        writeFileWithBootstrap(
                Pair.of(FIELD_NAME_HASH, randomHashString(r)),
                Pair.of(FIELD_NAME_TIMESTAMP, randomInstantString(r)));
        assertThrows(
                IOException.class, () -> EmergencyRecoveryFile.read(tmpDir), "Reading an invalid file should throw");
        writeFile(
                Pair.of(FIELD_NAME_ROUND, randomLongString(r)),
                Pair.of(FIELD_NAME_HASH, randomHashString(r)));
        assertDoesNotThrow(() -> EmergencyRecoveryFile.read(tmpDir), "Reading a valid file should not throw");
        writeFileWithBootstrap(
                Pair.of(FIELD_NAME_ROUND, randomLongString(r)),
                Pair.of(FIELD_NAME_HASH, randomHashString(r)));
        assertDoesNotThrow(() -> EmergencyRecoveryFile.read(tmpDir), "Reading a valid file should not throw");
        writeFile(
                Pair.of(FIELD_NAME_ROUND, randomLongString(r)),
                Pair.of(FIELD_NAME_TIMESTAMP, randomInstantString(r)));
        assertThrows(
                IOException.class, () -> EmergencyRecoveryFile.read(tmpDir), "Reading an invalid file should throw");
    }

    @Test
    void testHashValueMissing() {
        final Random r = RandomUtils.getRandomPrintSeed();
        writeFile(
                // the hash label is present, but the value is missing
                Pair.of("extra", "extra"),
                Pair.of(FIELD_NAME_ROUND, randomLongString(r)),
                Pair.of(FIELD_NAME_HASH, ""),
                Pair.of(FIELD_NAME_TIMESTAMP, randomInstantString(r)));
        assertThrows(IOException.class, () -> EmergencyRecoveryFile.read(tmpDir), "A value missing should throw");
    }

    @Test
    void testRoundValueMissing() {
        final Random r = RandomUtils.getRandomPrintSeed();
        writeFile(
                // the round label is present, but the value is missing
                Pair.of(FIELD_NAME_ROUND, ""),
                Pair.of(FIELD_NAME_HASH, randomHashString(r)),
                Pair.of(FIELD_NAME_TIMESTAMP, randomInstantString(r)));
        assertThrows(IOException.class, () -> EmergencyRecoveryFile.read(tmpDir), "A value missing should throw");
    }

    @Test
    void testTimestampValueMissing() {
        final Random r = RandomUtils.getRandomPrintSeed();
        writeFile(
                // the timestamp label is present, but the value is missing
                Pair.of(FIELD_NAME_ROUND, randomLongString(r)),
                Pair.of(FIELD_NAME_HASH, randomHashString(r)),
                Pair.of(FIELD_NAME_TIMESTAMP, ""));
        assertDoesNotThrow(() -> EmergencyRecoveryFile.read(tmpDir), "An optional value missing should not throw");
    }

    @Test
    void testFileDoesNotExist() throws IOException {
        assertNull(EmergencyRecoveryFile.read(tmpDir), "Reading from a file that does not exist should return null");
    }

    private EmergencyRecoveryFile createRecoveryFile(final Random r) {
        return new EmergencyRecoveryFile(r.nextLong(), randomHash(r), Instant.now());
    }

    private EmergencyRecoveryFile createRecoveryFileWithBootstrap(final Random r) {
        final EmergencyRecoveryFile orig = new EmergencyRecoveryFile(r.nextLong(), randomHash(r),
                Instant.ofEpochMilli(r.nextLong()));
        return new EmergencyRecoveryFile(orig.recovery().state(), Instant.ofEpochMilli(r.nextLong()));
    }

    @SafeVarargs
    private void writeFile(final Pair<String, ?>... values) {
        try (final BufferedWriter file =
                new BufferedWriter(new FileWriter(tmpDir.resolve(FILENAME).toFile()))) {
            file.write("recovery:\n");
            file.write("  state:\n");
            file.write(Arrays.stream(values)
                    .map(p -> "    " + p.getLeft() + ": " + p.getRight())
                    .collect(Collectors.joining("\n")));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SafeVarargs
    private void writeFileWithBootstrap(final Pair<String, ?>... values) {
        try (final BufferedWriter file =
                new BufferedWriter(new FileWriter(tmpDir.resolve(FILENAME).toFile()))) {
            file.write("recovery:\n");
            file.write("  state:\n");
            file.write(Arrays.stream(values)
                    .map(p -> "    " + p.getLeft() + ": " + p.getRight())
                    .collect(Collectors.joining("\n")));
            file.write("\n  bootstrap:\n");
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Hash randomHash(final Random r) {
        final byte[] bytes = new byte[DigestType.SHA_384.digestLength()];
        r.nextBytes(bytes);
        return new Hash(bytes, DigestType.SHA_384);
    }

    private static String randomHashString(final Random r) {
        return randomHash(r).toString();
    }

    private static String randomLongString(final Random r) {
        return Long.valueOf(r.nextLong()).toString();
    }

    private static String randomInstantString(final Random r) {
        return Instant.ofEpochMilli(r.nextLong()).toString();
    }
}
