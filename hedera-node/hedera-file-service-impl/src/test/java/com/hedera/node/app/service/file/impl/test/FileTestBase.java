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

package com.hedera.node.app.service.file.impl.test;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.protoToPbj;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hedera.test.utils.KeyUtils.A_KEY_LIST;
import static com.hedera.test.utils.KeyUtils.B_COMPLEX_KEY;
import static com.hedera.test.utils.KeyUtils.B_KEY_LIST;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.node.app.service.file.impl.ReadableFileStoreImpl;
import com.hedera.node.app.service.file.impl.ReadableUpgradeFileStoreImpl;
import com.hedera.node.app.service.file.impl.WritableFileStore;
import com.hedera.node.app.service.file.impl.WritableUpgradeFileStore;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKeyList;
import com.hedera.node.app.spi.fixtures.state.ListReadableQueueState;
import com.hedera.node.app.spi.fixtures.state.ListWritableQueueState;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.state.FilteredReadableStates;
import com.hedera.node.app.spi.state.FilteredWritableStates;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FileTestBase {
    protected static final String FILES = "FILES";
    protected static final String UPGRADE_FILE_KEY = "UPGRADE_FILE";
    protected static final String UPGRADE_DATA_KEY = "UPGRADE_DATA";
    protected final Key key = A_COMPLEX_KEY;
    protected final Key anotherKey = B_COMPLEX_KEY;
    protected final String payerIdLiteral = "0.0.3";
    protected final AccountID payerId = protoToPbj(asAccount(payerIdLiteral), AccountID.class);
    protected final AccountID autoRenewId = AccountID.newBuilder().accountNum(4).build();
    protected final byte[] contents = "contents".getBytes();
    protected final Bytes contentsBytes = Bytes.wrap(contents);

    protected final KeyList keys = A_KEY_LIST.keyList();
    protected final JKeyList jKeyList = new JKeyList(List.of(
            new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()),
            new JEd25519Key("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes()),
            new JEd25519Key("cccccccccccccccccccccccccccccccc".getBytes())));

    protected final KeyList anotherKeys = B_KEY_LIST.keyList();
    protected final FileID WELL_KNOWN_FILE_ID =
            FileID.newBuilder().fileNum(1_234L).build();
    protected final FileID WELL_KNOWN_UPGRADE_FILE_ID =
            FileID.newBuilder().fileNum(150L).shardNum(0L).realmNum(0L).build();
    protected final FileID WELL_KNOWN_SYSTEM_FILE_ID =
            FileID.newBuilder().fileNum(122L).shardNum(0L).realmNum(0L).build();
    protected final FileID fileId = WELL_KNOWN_FILE_ID;
    protected final FileID fileIdNotExist = FileID.newBuilder().fileNum(6_789L).build();
    protected final FileID fileSystemFileId = WELL_KNOWN_SYSTEM_FILE_ID;
    protected final FileID fileUpgradeFileId = WELL_KNOWN_UPGRADE_FILE_ID;
    protected final com.hederahashgraph.api.proto.java.FileID monoFileID =
            com.hederahashgraph.api.proto.java.FileID.newBuilder()
                    .setFileNum(1_234L)
                    .build();
    protected final Duration WELL_KNOWN_AUTO_RENEW_PERIOD =
            Duration.newBuilder().seconds(100).build();
    protected final Timestamp WELL_KNOWN_EXPIRY =
            Timestamp.newBuilder().seconds(1_234_567L).build();

    protected final String beneficiaryIdStr = "0.0.3";
    protected final long paymentAmount = 1_234L;
    protected final Bytes ledgerId = Bytes.wrap(new byte[] {0});
    protected final String memo = "test memo";
    protected final long expirationTime = 1_234_567L;
    protected final long sequenceNumber = 1L;
    protected final long autoRenewSecs = 100L;
    protected final AccountID TEST_DEFAULT_PAYER =
            AccountID.newBuilder().accountNum(13257).build();

    protected File file;

    protected File fileSystem;

    protected File upgradeFile;

    protected File fileWithNoKeysAndMemo;

    protected File fileWithNoContent;

    @Mock
    protected ReadableStates readableStates;

    @Mock
    protected WritableStates writableStates;

    @Mock
    protected FilteredReadableStates filteredReadableStates;

    @Mock
    protected FilteredWritableStates filteredWritableStates;

    @Mock(strictness = LENIENT)
    protected HandleContext handleContext;

    @Mock(strictness = LENIENT)
    protected SignatureVerification signatureVerification;

    protected MapReadableKVState<FileID, File> readableFileState;
    protected MapWritableKVState<FileID, File> writableFileState;

    protected ListReadableQueueState<ProtoBytes> readableUpgradeStates;
    protected ListWritableQueueState<ProtoBytes> writableUpgradeStates;

    protected MapReadableKVState<FileID, File> readableUpgradeFileStates;
    protected MapWritableKVState<FileID, File> writableUpgradeFileStates;

    protected ReadableFileStoreImpl readableStore;
    protected WritableFileStore writableStore;

    protected ReadableUpgradeFileStoreImpl readableUpgradeFileStore;
    protected WritableUpgradeFileStore writableUpgradeFileStore;

    @BeforeEach
    void commonSetUp() {
        givenValidFile();
        givenValidUpgradeFile(false, true);
        refreshStoresWithCurrentFileOnlyInReadable();
    }

    protected void refreshStoresWithCurrentFileOnlyInReadable() {
        readableFileState = readableFileState();
        writableFileState = emptyWritableFileState();
        readableUpgradeStates = readableUpgradeDataState();
        writableUpgradeStates = emptyUpgradeDataState();
        readableUpgradeFileStates = readableUpgradeFileState();
        writableUpgradeFileStates = emptyUpgradeFileState();
        given(readableStates.<FileID, File>get(FILES)).willReturn(readableFileState);
        given(writableStates.<FileID, File>get(FILES)).willReturn(writableFileState);
        given(filteredReadableStates.<ProtoBytes>getQueue(UPGRADE_DATA_KEY)).willReturn(readableUpgradeStates);
        given(filteredWritableStates.<ProtoBytes>getQueue(UPGRADE_DATA_KEY)).willReturn(writableUpgradeStates);
        given(filteredReadableStates.<FileID, File>get(FILES)).willReturn(readableUpgradeFileStates);
        given(filteredWritableStates.<FileID, File>get(FILES)).willReturn(writableUpgradeFileStates);
        readableStore = new ReadableFileStoreImpl(readableStates);
        writableStore = new WritableFileStore(writableStates);
        readableUpgradeFileStore = new ReadableUpgradeFileStoreImpl(filteredReadableStates);
        writableUpgradeFileStore = new WritableUpgradeFileStore(filteredWritableStates);

        given(handleContext.writableStore(WritableFileStore.class)).willReturn(writableStore);
        given(handleContext.writableStore(WritableUpgradeFileStore.class)).willReturn(writableUpgradeFileStore);
    }

    protected void refreshStoresWithCurrentFileInBothReadableAndWritable() {
        readableFileState = readableFileState();
        writableFileState = writableFileStateWithOneKey();
        readableUpgradeStates = readableUpgradeDataState();
        writableUpgradeStates = writableUpgradeDataState();
        readableUpgradeFileStates = readableUpgradeFileState();
        writableUpgradeFileStates = writableUpgradeFileState();
        given(readableStates.<FileID, File>get(FILES)).willReturn(readableFileState);
        given(writableStates.<FileID, File>get(FILES)).willReturn(writableFileState);
        given(filteredReadableStates.<ProtoBytes>getQueue(UPGRADE_DATA_KEY)).willReturn(readableUpgradeStates);
        given(filteredWritableStates.<ProtoBytes>getQueue(UPGRADE_DATA_KEY)).willReturn(writableUpgradeStates);
        given(filteredReadableStates.<FileID, File>get(FILES)).willReturn(readableUpgradeFileStates);
        given(filteredWritableStates.<FileID, File>get(FILES)).willReturn(writableUpgradeFileStates);
        readableStore = new ReadableFileStoreImpl(readableStates);
        writableStore = new WritableFileStore(writableStates);
        readableUpgradeFileStore = new ReadableUpgradeFileStoreImpl(filteredReadableStates);
        writableUpgradeFileStore = new WritableUpgradeFileStore(filteredWritableStates);

        given(handleContext.writableStore(WritableFileStore.class)).willReturn(writableStore);
        given(handleContext.writableStore(WritableUpgradeFileStore.class)).willReturn(writableUpgradeFileStore);
    }

    @NonNull
    protected MapWritableKVState<FileID, File> emptyWritableFileState() {
        return MapWritableKVState.<FileID, File>builder(FILES).build();
    }

    @NonNull
    protected ListWritableQueueState<ProtoBytes> emptyUpgradeDataState() {
        return ListWritableQueueState.<ProtoBytes>builder(UPGRADE_DATA_KEY).build();
    }

    @NonNull
    protected MapWritableKVState<FileID, File> emptyUpgradeFileState() {
        return MapWritableKVState.<FileID, File>builder(FILES).build();
    }

    @NonNull
    protected MapWritableKVState<FileID, File> writableFileStateWithOneKey() {
        return MapWritableKVState.<FileID, File>builder(FILES)
                .value(fileId, file)
                .value(fileSystemFileId, fileSystem)
                .build();
    }

    @NonNull
    protected MapWritableKVState<FileID, File> writableFileStateWithoutKey(File fileWithouthKey) {
        return MapWritableKVState.<FileID, File>builder(FILES)
                .value(fileId, fileWithouthKey)
                .build();
    }

    @NonNull
    protected MapReadableKVState<FileID, File> readableFileState() {
        return MapReadableKVState.<FileID, File>builder(FILES)
                .value(fileId, file)
                .build();
    }

    @NonNull
    protected ListReadableQueueState<ProtoBytes> readableUpgradeDataState() {
        return ListReadableQueueState.<ProtoBytes>builder(UPGRADE_DATA_KEY)
                .value(new ProtoBytes(fileSystem.contents()))
                .build();
    }

    @NonNull
    protected ListWritableQueueState<ProtoBytes> writableUpgradeDataState() {
        return ListWritableQueueState.<ProtoBytes>builder(UPGRADE_DATA_KEY)
                .value(new ProtoBytes(fileSystem.contents()))
                .build();
    }

    @NonNull
    protected MapReadableKVState<FileID, File> readableUpgradeFileState() {
        return MapReadableKVState.<FileID, File>builder(FILES)
                .value(fileUpgradeFileId, upgradeFile)
                .build();
    }

    @NonNull
    protected MapWritableKVState<FileID, File> writableUpgradeFileState() {
        return MapWritableKVState.<FileID, File>builder(FILES)
                .value(fileUpgradeFileId, upgradeFile)
                .build();
    }

    @NonNull
    protected MapReadableKVState<FileID, File> emptyReadableFileState() {
        return MapReadableKVState.<FileID, File>builder(FILES).build();
    }

    protected void givenValidFile() {
        givenValidFile(false);
    }

    protected void givenValidFile(boolean deleted) {
        givenValidFile(deleted, true);
    }

    protected void givenValidFile(boolean deleted, boolean withKeys) {
        file = new File(fileId, expirationTime, withKeys ? keys : null, Bytes.wrap(contents), memo, deleted, 0L);
        fileWithNoKeysAndMemo = new File(fileId, expirationTime, null, Bytes.wrap(contents), null, deleted, 0L);
        fileWithNoContent = new File(fileId, expirationTime, withKeys ? keys : null, null, memo, deleted, 0L);
        fileSystem = new File(
                fileSystemFileId, expirationTime, withKeys ? keys : null, Bytes.wrap(contents), memo, deleted, 0L);
    }

    protected void givenValidUpgradeFile(boolean deleted, boolean withKeys) {
        upgradeFile = new File(
                fileUpgradeFileId, expirationTime, withKeys ? keys : null, Bytes.wrap(contents), memo, deleted, 0L);
    }

    protected File createFile() {
        return new File.Builder()
                .fileId(fileId)
                .expirationSecond(expirationTime)
                .keys(keys)
                .contents(Bytes.wrap(contents))
                .memo(memo)
                .deleted(true)
                .build();
    }

    protected File createUpgradeFile() {
        return new File.Builder()
                .fileId(fileUpgradeFileId)
                .expirationSecond(expirationTime)
                .keys(keys)
                .contents(Bytes.wrap(contents))
                .memo(memo)
                .deleted(true)
                .build();
    }

    protected File createFileEmptyMemoAndKeys() {
        return new File.Builder()
                .fileId(fileId)
                .expirationSecond(expirationTime)
                .contents(Bytes.wrap(contents))
                .deleted(true)
                .build();
    }

    protected File createFileWithoutContent() {
        return new File.Builder()
                .fileId(fileId)
                .expirationSecond(expirationTime)
                .keys(keys)
                .memo(memo)
                .deleted(true)
                .build();
    }
}
