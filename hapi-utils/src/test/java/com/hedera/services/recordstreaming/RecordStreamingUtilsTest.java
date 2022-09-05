/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.recordstreaming;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RecordStreamingUtilsTest {

    private static final String PATH_TO_FILES = "src/test/resources/recordstream";

    private static final String V6_RECORD_FILE = "2022-08-15T14_02_51.642641228Z.rcd.gz";
    private static final String V6_RECORD_UNCOMPRESSED_FILE = "2022-08-18T09_37_10.411994657Z.rcd";
    private static final String V6_RECORD_SIGNATURE_FILE = "2022-06-14T14_49_22.456975294Z.rcd_sig";
    private static final String V6_SIDECAR_FILE = "2022-08-15T14_02_58.158861963Z_01.rcd.gz";
    private static final String V6_SIDECAR_UNCOMPRESSED_FILE =
            "2022-08-18T09_37_10.411994657Z_01.rcd";
    private static final String V5_RECORD_FILE = "V5_2022-05-27T08_27_14.157194938Z.rcd";
    private static final String V5_RECORD_SIGNATURE_FILE =
            "V5_2022-05-27T08_27_14.157194938Z.rcd_sig";

    @Test
    void parsingV6RecordFilesSucceeds() throws IOException {
        final var recordFilePath = Path.of(PATH_TO_FILES, V6_RECORD_FILE);

        final var recordFilePair =
                RecordStreamingUtils.readRecordStreamFile(recordFilePath.toString());

        assertEquals(6, recordFilePair.getLeft());
        assertTrue(recordFilePair.getRight().isPresent());
    }

    @Test
    void parsingUncompressedV6RecordFilesSucceeds() throws IOException {
        final var recordFilePath = Path.of(PATH_TO_FILES, V6_RECORD_UNCOMPRESSED_FILE);

        final var recordFilePair =
                RecordStreamingUtils.readUncompressedRecordStreamFile(recordFilePath.toString());

        assertEquals(6, recordFilePair.getLeft());
        assertTrue(recordFilePair.getRight().isPresent());
    }

    @Test
    void parsingV6SignatureRecordFilesSucceeds() throws IOException {
        final var signatureFilePath = Path.of(PATH_TO_FILES, V6_RECORD_SIGNATURE_FILE);

        final var signatureFilePair =
                RecordStreamingUtils.readSignatureFile(signatureFilePath.toString());

        assertEquals(6, signatureFilePair.getLeft());
        assertTrue(signatureFilePair.getRight().isPresent());
    }

    @Test
    void parsingV6SidecarRecordFilesSucceeds() throws IOException {
        final var sidecarFilePath = Path.of(PATH_TO_FILES, V6_SIDECAR_FILE);

        final var sidecarFile = RecordStreamingUtils.readSidecarFile(sidecarFilePath.toString());

        assertNotNull(sidecarFile);
    }

    @Test
    void parsingUncompressedV6SidecarRecordFilesSucceeds() throws IOException {
        final var sidecarFilePath = Path.of(PATH_TO_FILES, V6_SIDECAR_UNCOMPRESSED_FILE);

        final var sidecarFile =
                RecordStreamingUtils.readUncompressedSidecarFile(sidecarFilePath.toString());

        assertNotNull(sidecarFile);
    }

    @Test
    void parsingUnknownRecordFilesReturnsEmptyPair() {
        final var recordFilePath = Path.of(PATH_TO_FILES, V5_RECORD_FILE);

        assertThrows(
                IOException.class,
                () -> RecordStreamingUtils.readRecordStreamFile(recordFilePath.toString()));
    }

    @Test
    void parsingUnknownUncompressedRecordFilesReturnsEmptyPair() {
        final var recordFilePath = Path.of(PATH_TO_FILES, V5_RECORD_FILE);

        assertThrows(
                IOException.class,
                () ->
                        RecordStreamingUtils.readUncompressedRecordStreamFile(
                                recordFilePath.toString()));
    }

    @Test
    void parsingUnknownSignatureRecordFilesReturnsEmptyPair() {
        final var signatureFilePath = Path.of(PATH_TO_FILES, V5_RECORD_SIGNATURE_FILE);

        assertThrows(
                IOException.class,
                () -> RecordStreamingUtils.readSignatureFile(signatureFilePath.toString()));
    }

    @Test
    void parsingUnknownSidecarFileReturnsEmptyOptional() {
        final var notSidecarFilePath = Path.of(PATH_TO_FILES, V5_RECORD_SIGNATURE_FILE);

        assertThrows(
                IOException.class,
                () -> RecordStreamingUtils.readSidecarFile(notSidecarFilePath.toString()));
    }

    @Test
    void parsingUnknownUncompressedSidecarFileReturnsEmptyOptional() {
        final var notSidecarFilePath = Path.of(PATH_TO_FILES, V5_RECORD_SIGNATURE_FILE);

        assertThrows(
                IOException.class,
                () ->
                        RecordStreamingUtils.readUncompressedSidecarFile(
                                notSidecarFilePath.toString()));
    }
}
