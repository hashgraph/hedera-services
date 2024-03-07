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

package com.hedera.node.app.service.file.impl.test.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.impl.codec.FileServiceStateTranslator;
import com.hedera.node.app.service.file.impl.codec.FileServiceStateTranslator.FileMetadataAndContent;
import com.hedera.node.app.service.file.impl.test.FileTestBase;
import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import java.security.InvalidKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileServiceStateTranslatorTest extends FileTestBase {

    @Mock
    private com.hedera.node.app.service.mono.files.HederaFs hederaFs;

    @BeforeEach
    void setUp() {}

    @Test
    void createFileMetadataAndContentFromFile() throws InvalidKeyException {
        final var existingFile = readableStore.getFileMetadata(fileId);
        assertFalse(existingFile.deleted());

        final FileMetadataAndContent convertedFile = FileServiceStateTranslator.pbjToState(file);

        assertArrayEquals(
                convertedFile.data(), getExpectedMonoFileMetaAndContent().data());
        assertEquals(
                convertedFile.metadata().getExpiry(),
                getExpectedMonoFileMetaAndContent().metadata().getExpiry());
        assertEquals(
                convertedFile.metadata().getMemo(),
                getExpectedMonoFileMetaAndContent().metadata().getMemo());
        assertEquals(
                MiscUtils.describe(convertedFile.metadata().getWacl()),
                MiscUtils.describe(
                        getExpectedMonoFileMetaAndContent().metadata().getWacl()));
        assertEquals(
                convertedFile.metadata().isDeleted(),
                getExpectedMonoFileMetaAndContent().metadata().isDeleted());
    }

    @Test
    void createFileMetadataAndContentFromFileWithEmptyKeysAndMemo() throws InvalidKeyException {
        final var existingFile = readableStore.getFileMetadata(fileId);
        assertFalse(existingFile.deleted());

        final FileMetadataAndContent convertedFile = FileServiceStateTranslator.pbjToState(fileWithNoKeysAndMemo);

        assertArrayEquals(
                getExpectedMonoFileMetaAndContentWithEmptyMemoAndKeys().data(), convertedFile.data());
        assertEquals(
                getExpectedMonoFileMetaAndContentWithEmptyMemoAndKeys()
                        .metadata()
                        .getExpiry(),
                convertedFile.metadata().getExpiry());
        assertEquals(
                getExpectedMonoFileMetaAndContentWithEmptyMemoAndKeys()
                        .metadata()
                        .getMemo(),
                convertedFile.metadata().getMemo());
        assertEquals(
                MiscUtils.describe(getExpectedMonoFileMetaAndContentWithEmptyMemoAndKeys()
                        .metadata()
                        .getWacl()),
                MiscUtils.describe(convertedFile.metadata().getWacl()));
        assertEquals(
                getExpectedMonoFileMetaAndContentWithEmptyMemoAndKeys()
                        .metadata()
                        .isDeleted(),
                convertedFile.metadata().isDeleted());
    }

    @Test
    void createFileMetadataAndContentFromFileWithEmptyConentForDeletedFile() throws InvalidKeyException {

        final FileMetadataAndContent convertedFile = FileServiceStateTranslator.pbjToState(fileWithNoContent);

        assertArrayEquals(getExpectedMonoFileMetaAndContentEmptyContent().data(), convertedFile.data());
        assertEquals(
                getExpectedMonoFileMetaAndContentEmptyContent().metadata().getExpiry(),
                convertedFile.metadata().getExpiry());
        assertEquals(
                getExpectedMonoFileMetaAndContentEmptyContent().metadata().getMemo(),
                convertedFile.metadata().getMemo());
        assertEquals(
                MiscUtils.describe(getExpectedMonoFileMetaAndContentEmptyContent()
                        .metadata()
                        .getWacl()),
                MiscUtils.describe(convertedFile.metadata().getWacl()));
        assertEquals(
                getExpectedMonoFileMetaAndContentEmptyContent().metadata().isDeleted(),
                convertedFile.metadata().isDeleted());
    }

    @Test
    void createFileMetadataAndContentFromReadableFileStore() throws InvalidKeyException {
        final var existingFile = readableStore.getFileMetadata(fileId);
        assertFalse(existingFile.deleted());

        final FileMetadataAndContent convertedFile = FileServiceStateTranslator.pbjToState(fileId, readableStore);

        assertArrayEquals(
                convertedFile.data(), getExpectedMonoFileMetaAndContent().data());
        assertEquals(
                convertedFile.metadata().getExpiry(),
                getExpectedMonoFileMetaAndContent().metadata().getExpiry());
        assertEquals(
                convertedFile.metadata().getMemo(),
                getExpectedMonoFileMetaAndContent().metadata().getMemo());
        assertEquals(
                MiscUtils.describe(convertedFile.metadata().getWacl()),
                MiscUtils.describe(
                        getExpectedMonoFileMetaAndContent().metadata().getWacl()));
        assertEquals(
                convertedFile.metadata().isDeleted(),
                getExpectedMonoFileMetaAndContent().metadata().isDeleted());
    }

    @Test
    void createFileFromMetadataContentAndFileId() {
        final byte[] data = contents;
        final com.hedera.node.app.service.mono.files.HFileMeta metadata =
                new HFileMeta(true, jKeyList, expirationTime, memo);

        final com.hederahashgraph.api.proto.java.FileID fileID = monoFileID;

        final File convertedFile = FileServiceStateTranslator.stateToPbj(data, metadata, fileID);

        assertEquals(createFile(), convertedFile);
    }

    @Test
    void createFileFromMetadataContentAndFileIdWithEmptyMemoAndKeys() {
        final byte[] data = contents;
        final com.hedera.node.app.service.mono.files.HFileMeta metadata = new HFileMeta(true, null, expirationTime);

        final com.hederahashgraph.api.proto.java.FileID fileID = monoFileID;

        final File convertedFile = FileServiceStateTranslator.stateToPbj(data, metadata, fileID);

        assertEquals(createFileEmptyMemoAndKeys(), convertedFile);
    }

    @Test
    void createFileFromMetadataContentAndFileIdWithoutContentForDeletedFile() {
        final byte[] data = null;
        final com.hedera.node.app.service.mono.files.HFileMeta metadata =
                new HFileMeta(true, jKeyList, expirationTime, memo);

        final com.hederahashgraph.api.proto.java.FileID fileID = monoFileID;

        final File convertedFile = FileServiceStateTranslator.stateToPbj(data, metadata, fileID);

        assertEquals(createFileWithoutContent(), convertedFile);
    }

    @Test
    void createFileFromFileIDAndHederaFs() {

        final com.hederahashgraph.api.proto.java.FileID fileID = monoFileID;
        final com.hedera.node.app.service.mono.files.HFileMeta metadata =
                new HFileMeta(true, jKeyList, expirationTime, memo);

        given(hederaFs.cat(fileID)).willReturn(contents);
        given(hederaFs.getattr(fileID)).willReturn(metadata);
        final File convertedFile = FileServiceStateTranslator.stateToPbj(fileID, hederaFs);

        assertEquals(createFile(), convertedFile);
    }

    private FileMetadataAndContent getExpectedMonoFileMetaAndContent() throws InvalidKeyException {
        var keys = com.hedera.node.app.service.mono.legacy.core.jproto.JKey.convertKey(
                Key.newBuilder().keyList(file.keys()).build(), 1);
        com.hedera.node.app.service.mono.files.HFileMeta hFileMeta =
                new HFileMeta(file.deleted(), keys, file.expirationSecond(), file.memo());
        return new FileMetadataAndContent(file.contents().toByteArray(), hFileMeta);
    }

    private FileMetadataAndContent getExpectedMonoFileMetaAndContentWithEmptyMemoAndKeys() {
        com.hedera.node.app.service.mono.files.HFileMeta hFileMeta =
                new HFileMeta(file.deleted(), null, file.expirationSecond(), "");
        return new FileMetadataAndContent(file.contents().toByteArray(), hFileMeta);
    }

    private FileMetadataAndContent getExpectedMonoFileMetaAndContentEmptyContent() throws InvalidKeyException {
        var keys = com.hedera.node.app.service.mono.legacy.core.jproto.JKey.convertKey(
                Key.newBuilder().keyList(file.keys()).build(), 1);
        com.hedera.node.app.service.mono.files.HFileMeta hFileMeta =
                new HFileMeta(file.deleted(), keys, file.expirationSecond(), file.memo());
        return new FileMetadataAndContent(new byte[] {}, hFileMeta);
    }
}
