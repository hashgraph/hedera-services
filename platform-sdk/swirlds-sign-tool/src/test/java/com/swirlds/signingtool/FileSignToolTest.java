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

package com.swirlds.signingtool;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.computeEntireHash;
import static com.swirlds.common.stream.internal.LinkedObjectStreamValidateUtils.validateFileAndSignature;
import static com.swirlds.common.stream.internal.StreamValidationResult.OK;
import static com.swirlds.signingtool.FileSignTool.ALIAS_PROPERTY;
import static com.swirlds.signingtool.FileSignTool.DEST_DIR_PROPERTY;
import static com.swirlds.signingtool.FileSignTool.DIR_PROPERTY;
import static com.swirlds.signingtool.FileSignTool.FILE_NAME_PROPERTY;
import static com.swirlds.signingtool.FileSignTool.KEY_PROPERTY;
import static com.swirlds.signingtool.FileSignTool.LOG_CONFIG_PROPERTY;
import static com.swirlds.signingtool.FileSignTool.PASSWORD_PROPERTY;
import static com.swirlds.signingtool.FileSignTool.TYPE_FILE_HASH;
import static com.swirlds.signingtool.FileSignTool.TYPE_SIGNATURE;
import static com.swirlds.signingtool.FileSignTool.buildDestSigFilePath;
import static com.swirlds.signingtool.FileSignTool.integerToBytes;
import static com.swirlds.signingtool.FileSignTool.loadPfxKey;
import static com.swirlds.signingtool.FileSignTool.prepare;
import static com.swirlds.signingtool.FileSignTool.sign;
import static com.swirlds.signingtool.FileSignTool.signSingleFile;
import static com.swirlds.signingtool.FileSignTool.signSingleFileOldVersion;
import static com.swirlds.signingtool.FileSignTool.verifySignature;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.stream.StreamType;
import com.swirlds.common.stream.internal.StreamValidationResult;
import com.swirlds.test.framework.TestQualifierTags;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class FileSignToolTest {
    private static final String ALIAS = "s-alice";
    private static final String PASSWORD = "password";
    private static final String KEY_PATH = "src/test/resources/signingTool/private-alice.pfx";
    private static final String DIR_PATH = "src/test/resources/signingTool/";
    private static final String EVTS_FILE_PATH = DIR_PATH + "2021-04-18T04_23_37.200170000Z.evts";
    private static final String JSON_DIR_PATH = DIR_PATH + "json/";
    private static final String JSON_FILE_PATH = JSON_DIR_PATH + "recordStreamType.json";
    private static final String EXPECTED_SIG_PATH =
            "src/test/resources/signingTool/expectedSig/2021-04-18T04_23_37.200170000Z.evts_sig";

    private static final String CONTENT_SHOULD_MATCH_MSG = "loaded StreamType should match the json file";

    private static final String SIG_VALID_MSG = "the signature bytes should be valid";

    private static final KeyPair KEY_PAIR = loadPfxKey(KEY_PATH, PASSWORD, ALIAS);
    private static final int MAX_ADDRESSBOOK_SIZE = 2048;

    @BeforeAll
    public static void setup() throws ConstructableRegistryException {
        SettingsCommon.maxAddressSizeAllowed = MAX_ADDRESSBOOK_SIZE;
    }

    @Test
    void loadJsonTest() throws IOException {
        final StreamType loadedStreamType = FileSignTool.loadStreamTypeFromJson(JSON_FILE_PATH);

        final String expectedDesc = "records";
        final String expectedExtension = "rcd";
        final String expectedSigExtension = "rcd_sig";
        final int[] expectedFileHeader = {5, 0, 11, 0};
        final byte[] expectedSigFileHeader = {5};

        assertEquals(expectedDesc, loadedStreamType.getDescription(), CONTENT_SHOULD_MATCH_MSG);
        assertEquals(expectedExtension, loadedStreamType.getExtension(), CONTENT_SHOULD_MATCH_MSG);
        assertEquals(expectedSigExtension, loadedStreamType.getSigExtension(), CONTENT_SHOULD_MATCH_MSG);
        assertArrayEquals(expectedFileHeader, loadedStreamType.getFileHeader(), CONTENT_SHOULD_MATCH_MSG);
        assertArrayEquals(expectedSigFileHeader, loadedStreamType.getSigFileHeader(), CONTENT_SHOULD_MATCH_MSG);
    }

    @Test
    void buildDestSigFilePathTest() {
        final File destDir = new File("test/path/dir");
        final File streamFile = new File("recordStream/path/test.rcd");
        final String expectedPath = "test/path/dir/test.rcd_sig";
        assertEquals(expectedPath, buildDestSigFilePath(destDir, streamFile), "the path should match expected");
    }

    @Test
    void intToBytesTest() {
        final int num = ThreadLocalRandom.current().nextInt();
        final byte[] bytes = integerToBytes(num);
        assertEquals(num, ByteBuffer.wrap(bytes).getInt(), "two number should be equal");
    }

    @Test
    void loadPfxKeyTest() {
        final KeyPair keyPair = loadPfxKey(KEY_PATH, PASSWORD, ALIAS);
        assertNotNull(keyPair, "the keyPair should not be null");
    }

    @Test
    void signAndValidateTest() throws Exception {
        final File eventStreamFile = new File(EVTS_FILE_PATH);
        final byte[] entireHashValue = computeEntireHash(eventStreamFile).getValue();
        final byte[] sigBytes = sign(entireHashValue, KEY_PAIR);
        assertTrue(
                verifySignature(entireHashValue, sigBytes, KEY_PAIR.getPublic(), "tempPath.evts_sig"), SIG_VALID_MSG);
    }

    @Test
    void signSingleFileOldVersionTest() throws Exception {
        final File eventStreamFile = new File(EVTS_FILE_PATH);
        final String destSigFilePath = "src/test/resources/signingTool/v3Sig.evts_sig";
        // generates sig file
        signSingleFileOldVersion(KEY_PAIR, eventStreamFile, destSigFilePath);
        final File sigFile = new File(destSigFilePath);
        // verifies signature
        assertTrue(verifyOldSignatureFile(sigFile, KEY_PAIR.getPublic()), SIG_VALID_MSG);
        // deletes generated sig file
        sigFile.deleteOnExit();
    }

    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void signSingleFileTest() throws Exception {
        // register constructables and set settings
        prepare(EventStreamType.getInstance());

        final File eventStreamFile = new File(EVTS_FILE_PATH);
        final File destDir = new File(DIR_PATH);
        // generates sig file
        signSingleFile(KEY_PAIR, eventStreamFile, destDir, EventStreamType.getInstance());
        final File sigFile = new File(buildDestSigFilePath(destDir, eventStreamFile));

        final StreamValidationResult validationResult =
                validateFileAndSignature(eventStreamFile, sigFile, KEY_PAIR.getPublic(), EventStreamType.getInstance());
        assertEquals(OK, validationResult, SIG_VALID_MSG);
        // generated signature file should match expected
        compareSigWithExpectedThenDelete();
    }

    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void mainSignSingleFileTest() throws IOException {
        setProperties();
        System.setProperty(FILE_NAME_PROPERTY, EVTS_FILE_PATH);
        FileSignTool.main(new String[] {});
        // generated signature file should match expected, then delete the sig file
        compareSigWithExpectedThenDelete();
        clearProperties();
    }

    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void mainSignDirTest() throws IOException {
        setProperties();
        System.setProperty(DIR_PROPERTY, JSON_DIR_PATH);
        FileSignTool.main(new String[] {});
        final File sigFileForJson = new File(buildDestSigFilePath(new File(DIR_PATH), new File(JSON_FILE_PATH)));
        // the file should not exist, because when `-Ddir` is set, the tool only signs .rcd/.evts/.csv files
        assertTrue(
                !sigFileForJson.exists(),
                "should not generate signature file when signing a directory which doesn't contain any .rcd/.evts/"
                        + ".csv"
                        + " "
                        + "files");
        clearProperties();
    }

    private void setProperties() {
        System.setProperty(LOG_CONFIG_PROPERTY, "src/test/resources/log4j2ForTest.xml");
        System.setProperty(KEY_PROPERTY, KEY_PATH);
        System.setProperty(DEST_DIR_PROPERTY, DIR_PATH);
        System.setProperty(ALIAS_PROPERTY, ALIAS);
        System.setProperty(PASSWORD_PROPERTY, PASSWORD);
    }

    private void clearProperties() {
        System.clearProperty(LOG_CONFIG_PROPERTY);
        System.clearProperty(KEY_PROPERTY);
        System.clearProperty(DEST_DIR_PROPERTY);
        System.clearProperty(ALIAS_PROPERTY);
        System.clearProperty(PASSWORD_PROPERTY);
        System.clearProperty(FILE_NAME_PROPERTY);
        System.clearProperty(DIR_PROPERTY);
    }

    private void compareSigWithExpectedThenDelete() throws IOException {
        final File sigFile = new File(buildDestSigFilePath(new File(DIR_PATH), new File(EVTS_FILE_PATH)));
        // the content of generated file should be the same as expected signature file
        final File expectedSigFile = new File(EXPECTED_SIG_PATH);

        assertEquals(
                -1,
                Files.mismatch(sigFile.toPath(), expectedSigFile.toPath()),
                "generated signature file should match expected signature file");
        // deletes generated sig file
        sigFile.deleteOnExit();
    }

    private static boolean verifyOldSignatureFile(final File sigFile, final PublicKey publicKey) throws IOException {
        // parses the file
        final Pair<byte[], byte[]> parsedPair = parseOldSigFile(sigFile);
        final byte[] hashBytes = parsedPair.getLeft();
        final byte[] sigBytes = parsedPair.getRight();
        // verifies signature
        return verifySignature(hashBytes, sigBytes, publicKey, sigFile.getName());
    }

    /**
     * Read the file hash and the signature byte array contained in the signature file; return a pair of FileHash and
     * signature
     *
     * @param file an signature file with old format
     * @return a pair of hash bytes and signature bytes
     */
    private static Pair<byte[], byte[]> parseOldSigFile(final File file) throws IOException {
        if (!file.getName().endsWith("_sig")) {
            return null;
        }
        try (final DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            byte[] fileHash = null;
            byte[] sig = null;
            while (dis.available() != 0) {
                final byte typeDelimiter = dis.readByte();
                switch (typeDelimiter) {
                    case TYPE_FILE_HASH:
                        fileHash = new byte[48];
                        dis.readFully(fileHash);
                        break;
                    case TYPE_SIGNATURE:
                        final int sigLength = dis.readInt();
                        sig = new byte[sigLength];
                        dis.readFully(sig);
                        break;
                    default:
                        throw new IOException(
                                String.format("Unknown file delimiter %d in file %s", typeDelimiter, file.getName()));
                }
            }
            return Pair.of(fileHash, sig);
        }
    }
}
