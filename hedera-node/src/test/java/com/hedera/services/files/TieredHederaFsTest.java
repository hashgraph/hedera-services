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
package com.hedera.services.files;

import static com.hedera.services.files.TieredHederaFs.BYTES_PER_KB;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.files.TieredHederaFs.IllegalArgumentType;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TieredHederaFsTest {
    private static final Instant now = Instant.now();

    private static final int lifetimeSecs = 1_234_567;
    private static final JKey validKey = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKeyUnchecked();
    private static final byte[] origContents = "Where, like a pillow on a bed /".getBytes();
    private static final byte[] newContents =
            "Where, like a pillow on a bed / A pregnant bank swelled up to rest /".getBytes();
    private static final byte[] moreContents = "The violet's reclining head".getBytes();
    private HFileMeta deadAttr;
    private HFileMeta livingAttr;
    private HFileMeta deletedAttr;
    private static final FileID fid = IdUtils.asFile("0.0.7575");
    private static final FileID missing = IdUtils.asFile("0.0.666");
    private static final AccountID sponsor = IdUtils.asAccount("0.0.2");

    private FileUpdateInterceptor noInterceptor;
    private FileUpdateInterceptor lowInterceptor;
    private FileUpdateInterceptor highInterceptor;

    private EntityIdSource ids;
    private GlobalDynamicProperties properties;
    private Supplier<Instant> clock;
    private Map<FileID, byte[]> data;
    private Map<FileID, HFileMeta> metadata;
    private MerkleSpecialFiles specialFiles;
    private TieredHederaFs subject;

    @BeforeEach
    void setup() {
        deadAttr = new HFileMeta(false, validKey, now.getEpochSecond() - 1);
        livingAttr = new HFileMeta(false, validKey, now.getEpochSecond() + lifetimeSecs);
        deletedAttr = new HFileMeta(true, validKey, now.getEpochSecond() + lifetimeSecs);

        noInterceptor = mock(FileUpdateInterceptor.class);
        given(noInterceptor.priorityForCandidate(any())).willReturn(OptionalInt.empty());

        lowInterceptor = mock(FileUpdateInterceptor.class);
        given(lowInterceptor.priorityForCandidate(any()))
                .willReturn(OptionalInt.of(Integer.MAX_VALUE));
        highInterceptor = mock(FileUpdateInterceptor.class);
        given(highInterceptor.priorityForCandidate(any()))
                .willReturn(OptionalInt.of(Integer.MIN_VALUE));

        ids = mock(EntityIdSource.class);
        data = mock(Map.class);
        metadata = mock(Map.class);
        specialFiles = mock(MerkleSpecialFiles.class);

        clock = mock(Supplier.class);
        given(clock.get()).willReturn(now);

        properties = mock(GlobalDynamicProperties.class);
        given(properties.maxFileSizeKb()).willReturn(1);

        subject = new TieredHederaFs(ids, properties, clock, data, metadata, () -> specialFiles);
    }

    @Test
    void gettersWork() {
        assertEquals(data, subject.getData());
        assertEquals(metadata, subject.getMetadata());
        assertEquals(specialFiles, subject.specialFiles());
    }

    @Test
    void interceptorsAreRegistered() {
        subject.register(lowInterceptor);
        subject.register(highInterceptor);

        assertEquals(lowInterceptor, subject.updateInterceptors.get(0));
        assertEquals(highInterceptor, subject.updateInterceptors.get(1));
        assertEquals(2, subject.numRegisteredInterceptors());
    }

    @Test
    void appendsWithExpectedResultSansInterception() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);
        given(data.get(fid)).willReturn(origContents);

        final var result = subject.append(fid, moreContents);

        assertEquals(SUCCESS, result.outcome());
        assertTrue(result.fileReplaced());
        verify(data)
                .put(
                        argThat(fid::equals),
                        argThat(
                                bytes ->
                                        new String(bytes)
                                                .equals(
                                                        new String(origContents)
                                                                + new String(moreContents))));
    }

    @Test
    void overwritesWithExpectedResultSansInterception() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);

        final var result = subject.overwrite(fid, newContents);

        assertEquals(SUCCESS, result.outcome());
        assertTrue(result.fileReplaced());
        verify(data).put(fid, newContents);
    }

    @Test
    void highPriorityInterceptorSetsOutcome() {
        given(highInterceptor.preUpdate(fid, newContents))
                .willReturn(new AbstractMap.SimpleEntry<>(ResponseCodeEnum.FAIL_FEE, true));
        given(lowInterceptor.preUpdate(fid, newContents))
                .willReturn(new AbstractMap.SimpleEntry<>(ResponseCodeEnum.FAIL_INVALID, false));
        subject.register(lowInterceptor);
        subject.register(highInterceptor);
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);

        final var result = subject.overwrite(fid, newContents);

        assertEquals(ResponseCodeEnum.FAIL_FEE, result.outcome());
        assertFalse(result.fileReplaced());
        verify(data, never()).put(fid, newContents);
    }

    @Test
    void relevantInterceptorsGetPostCb() {
        final var inOrder = inOrder(highInterceptor, lowInterceptor);
        given(highInterceptor.preUpdate(fid, newContents))
                .willReturn(new AbstractMap.SimpleEntry<>(ResponseCodeEnum.OK, true));
        given(lowInterceptor.preUpdate(fid, newContents))
                .willReturn(new AbstractMap.SimpleEntry<>(ResponseCodeEnum.OK, true));
        subject.register(lowInterceptor);
        subject.register(highInterceptor);
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);

        final var result = subject.overwrite(fid, newContents);

        assertEquals(ResponseCodeEnum.OK, result.outcome());
        assertTrue(result.fileReplaced());
        verify(data).put(fid, newContents);
        inOrder.verify(highInterceptor).postUpdate(fid, newContents);
        inOrder.verify(lowInterceptor).postUpdate(fid, newContents);
    }

    @Test
    void shortCircuitsIfInterceptorRejects() {
        given(highInterceptor.preUpdate(fid, newContents))
                .willReturn(
                        new AbstractMap.SimpleEntry<>(
                                ResponseCodeEnum.AUTHORIZATION_FAILED, false));
        given(lowInterceptor.preUpdate(fid, newContents))
                .willReturn(new AbstractMap.SimpleEntry<>(ResponseCodeEnum.OK, true));
        subject.register(lowInterceptor);
        subject.register(highInterceptor);
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);

        final var result = subject.overwrite(fid, newContents);

        assertEquals(ResponseCodeEnum.AUTHORIZATION_FAILED, result.outcome());
        assertFalse(result.fileReplaced());
        verify(data, never()).put(fid, newContents);
    }

    @Test
    void appendRejectsOversizeContents() {
        final var stretchContents = new byte[BYTES_PER_KB - 1];
        final var burstContents = new byte[2];
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);
        given(data.get(fid)).willReturn(stretchContents);
        given(properties.maxFileSizeKb()).willReturn(1);

        final var iae =
                assertThrows(
                        IllegalArgumentException.class, () -> subject.append(fid, burstContents));

        assertEquals(
                IllegalArgumentType.OVERSIZE_CONTENTS,
                IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void assertIllegalArgumentTypeGuggestedStatus() {
        assertEquals(
                ResponseCodeEnum.FILE_DELETED, IllegalArgumentType.DELETED_FILE.suggestedStatus());
        assertEquals(
                ResponseCodeEnum.INVALID_FILE_ID,
                IllegalArgumentType.UNKNOWN_FILE.suggestedStatus());
        assertEquals(
                ResponseCodeEnum.INVALID_EXPIRATION_TIME,
                IllegalArgumentType.FILE_WOULD_BE_EXPIRED.suggestedStatus());
        assertEquals(
                ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED,
                IllegalArgumentType.OVERSIZE_CONTENTS.suggestedStatus());
    }

    @Test
    void appendAllowsOversizeContentsForDiskFs() {
        final var stretchContents = new byte[BYTES_PER_KB - 1];
        final var burstContents = new byte[2];
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);
        given(specialFiles.contains(fid)).willReturn(true);
        given(specialFiles.get(fid)).willReturn(stretchContents);
        // and:
        given(properties.maxFileSizeKb()).willReturn(1);

        final var result = subject.append(fid, burstContents);

        assertEquals(SUCCESS, result.outcome());
        assertTrue(result.fileReplaced());
        // and:
        verify(specialFiles)
                .append(
                        argThat(fid::equals),
                        argThat((byte[] bytes) -> Arrays.equals(bytes, burstContents)));
    }

    @Test
    void overwritePermitsOversizeContentsForDiskFs() {
        final var oversizeContents = new byte[BYTES_PER_KB + 1];
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);
        given(specialFiles.contains(fid)).willReturn(true);
        given(properties.maxFileSizeKb()).willReturn(1);

        final var result = subject.overwrite(fid, oversizeContents);

        assertEquals(SUCCESS, result.outcome());
        assertTrue(result.fileReplaced());
        // and:
        verify(specialFiles).update(fid, oversizeContents);
    }

    @Test
    void overwriteRejectsOversizeContents() {
        final var oversizeContents = new byte[BYTES_PER_KB + 1];
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);
        given(properties.maxFileSizeKb()).willReturn(1);

        final var iae =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> subject.overwrite(fid, oversizeContents));

        assertEquals(
                IllegalArgumentType.OVERSIZE_CONTENTS,
                IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void rmThrowsOnMissing() {
        given(metadata.containsKey(missing)).willReturn(false);

        final var iae = assertThrows(IllegalArgumentException.class, () -> subject.rm(missing));

        assertEquals(
                IllegalArgumentType.UNKNOWN_FILE, IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void rmPurgesAsExpected() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(deletedAttr);
        given(data.containsKey(fid)).willReturn(true);

        subject.rm(fid);

        verify(metadata).remove(fid);
        verify(data).remove(fid);
    }

    @Test
    void deleteThrowsOnMissing() {
        given(metadata.containsKey(missing)).willReturn(false);

        assertFailsWith(() -> subject.delete(missing), INVALID_FILE_ID);
    }

    @Test
    void deleteThrowsOnDeleted() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(deletedAttr);

        assertFailsWith(() -> subject.delete(fid), FILE_DELETED);
    }

    @Test
    void deleteRespectsInterceptors() {
        final var authPolicy = mock(FileUpdateInterceptor.class);
        given(authPolicy.priorityForCandidate(fid)).willReturn(OptionalInt.of(Integer.MIN_VALUE));
        given(authPolicy.preDelete(fid))
                .willReturn(new AbstractMap.SimpleEntry<>(AUTHORIZATION_FAILED, false));
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);

        subject.register(authPolicy);

        assertFailsWith(() -> subject.delete(fid), AUTHORIZATION_FAILED);
        verify(metadata, never()).put(argThat(fid::equals), any());
        verify(data, never()).remove(fid);
    }

    @Test
    void deleteUpdatesAttrAsExpected() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);

        subject.delete(fid);

        verify(metadata)
                .put(
                        argThat(fid::equals),
                        argThat(
                                attr ->
                                        attr.isDeleted()
                                                && attr.getExpiry() == livingAttr.getExpiry()
                                                && attr.getWacl().equals(livingAttr.getWacl())));
        verify(data).remove(fid);
        assertTrue(subject.getattr(fid).isDeleted());
    }

    @Test
    void appendThrowsOnMissing() {
        given(metadata.containsKey(missing)).willReturn(false);

        final var iae =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> subject.append(missing, moreContents));

        assertEquals(
                IllegalArgumentType.UNKNOWN_FILE, IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void overwriteThrowsOnMissing() {
        given(metadata.containsKey(missing)).willReturn(false);

        final var iae =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> subject.overwrite(missing, newContents));

        assertEquals(
                IllegalArgumentType.UNKNOWN_FILE, IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void appendThrowsOnDeleted() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(deletedAttr);

        final var iae =
                assertThrows(
                        IllegalArgumentException.class, () -> subject.append(fid, moreContents));

        assertEquals(
                IllegalArgumentType.DELETED_FILE, IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void overwriteThrowsOnDeleted() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(deletedAttr);

        final var iae =
                assertThrows(
                        IllegalArgumentException.class, () -> subject.overwrite(fid, newContents));

        assertEquals(
                IllegalArgumentType.DELETED_FILE, IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void lsThrowsOnMissing() {
        given(metadata.containsKey(missing)).willReturn(false);

        final var iae =
                assertThrows(IllegalArgumentException.class, () -> subject.getattr(missing));

        assertEquals(
                IllegalArgumentType.UNKNOWN_FILE, IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void lsGetsExpected() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(deletedAttr);

        final var meta = subject.getattr(fid);

        assertEquals(deletedAttr, meta);
    }

    @Test
    void catThrowsOnMissing() {
        given(metadata.containsKey(missing)).willReturn(false);

        final var iae = assertThrows(IllegalArgumentException.class, () -> subject.cat(missing));

        assertEquals(
                IllegalArgumentType.UNKNOWN_FILE, IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void catThrowsOnDeleted() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(deletedAttr);

        final var iae = assertThrows(IllegalArgumentException.class, () -> subject.cat(fid));

        assertEquals(
                IllegalArgumentType.DELETED_FILE, IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void catGetsExpected() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);
        given(specialFiles.contains(fid)).willReturn(false);
        given(data.get(fid)).willReturn(origContents);

        final var contents = subject.cat(fid);

        assertArrayEquals(origContents, contents);
        verify(specialFiles, never()).get(any());
    }

    @Test
    void catGetsExpectedFromDisk() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);
        given(specialFiles.contains(fid)).willReturn(true);
        given(specialFiles.get(fid)).willReturn(origContents);

        final var contents = subject.cat(fid);

        assertArrayEquals(origContents, contents);
        verify(data, never()).get(any());
    }

    @Test
    void catGetsExpectedFromSpecialFiles() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);
        given(specialFiles.contains(fid)).willReturn(true);
        given(specialFiles.get(fid)).willReturn(origContents);

        // when:
        var contents = subject.cat(fid);

        // then:
        assertEquals(new String(origContents), new String(contents));
    }

    @Test
    void usesMetadataToCheckExistence() {
        given(metadata.containsKey(fid)).willReturn(true);

        final var yesFlag = subject.exists(fid);
        final var noFlag = subject.exists(missing);

        assertTrue(yesFlag);
        assertFalse(noFlag);
        verify(metadata).containsKey(fid);
        verify(metadata).containsKey(missing);
    }

    @Test
    void sudoSetattrAllowsDeletedFile() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(deletedAttr);

        final var result = subject.sudoSetattr(fid, livingAttr);

        verify(metadata).put(fid, livingAttr);
        assertTrue(result.attrChanged());
        assertFalse(result.fileReplaced());
        assertEquals(SUCCESS, result.outcome());
    }

    @Test
    void setattrRejectsDeletedFile() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(deletedAttr);

        final var iae =
                assertThrows(
                        IllegalArgumentException.class, () -> subject.setattr(fid, livingAttr));

        assertEquals(
                IllegalArgumentType.DELETED_FILE, IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void sudoSetattrRejectsMissingFile() {
        given(metadata.containsKey(missing)).willReturn(false);

        final var iae =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> subject.sudoSetattr(missing, livingAttr));

        assertEquals(
                IllegalArgumentType.UNKNOWN_FILE, IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void setattrRejectsMissingFile() {
        given(metadata.containsKey(missing)).willReturn(false);

        final var iae =
                assertThrows(
                        IllegalArgumentException.class, () -> subject.setattr(missing, livingAttr));

        assertEquals(
                IllegalArgumentType.UNKNOWN_FILE, IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void setattrUpdatesAsExpected() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);

        final var result = subject.setattr(fid, livingAttr);

        verify(metadata).put(fid, livingAttr);
        assertTrue(result.attrChanged());
        assertFalse(result.fileReplaced());
        assertEquals(SUCCESS, result.outcome());
    }

    @Test
    void setattrRespectsInterceptors() {
        final var authPolicy = mock(FileUpdateInterceptor.class);
        given(authPolicy.priorityForCandidate(fid)).willReturn(OptionalInt.of(Integer.MIN_VALUE));
        given(authPolicy.preAttrChange(argThat(fid::equals), any()))
                .willReturn(new AbstractMap.SimpleEntry<>(AUTHORIZATION_FAILED, false));
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);

        subject.register(authPolicy);
        final var result = subject.setattr(fid, livingAttr);

        verify(metadata, never()).put(fid, livingAttr);
        assertFalse(result.attrChanged());
        assertFalse(result.fileReplaced());
        assertEquals(AUTHORIZATION_FAILED, result.outcome());
    }

    @Test
    void setattrRejectsExpiredFile() {
        given(metadata.containsKey(fid)).willReturn(true);
        given(metadata.get(fid)).willReturn(livingAttr);

        final var iae =
                assertThrows(IllegalArgumentException.class, () -> subject.setattr(fid, deadAttr));

        assertEquals(
                IllegalArgumentType.FILE_WOULD_BE_EXPIRED,
                IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void createRejectsExpiredFile() {
        final var iae =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> subject.create(origContents, deadAttr, sponsor));

        assertEquals(
                IllegalArgumentType.FILE_WOULD_BE_EXPIRED,
                IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void createRejectsOversizeContents() {
        given(properties.maxFileSizeKb()).willReturn(1);
        final var oversizeContents = new byte[BYTES_PER_KB + 1];

        final var iae =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> subject.create(oversizeContents, livingAttr, sponsor));

        assertEquals(
                IllegalArgumentType.OVERSIZE_CONTENTS,
                IllegalArgumentType.valueOf(iae.getMessage()));
    }

    @Test
    void createUsesNextEntityId() {
        given(ids.newFileId(sponsor)).willReturn(fid);

        final var newFile = subject.create(origContents, livingAttr, sponsor);

        assertEquals(fid, newFile);
        verify(data).put(fid, origContents);
        verify(metadata).put(fid, livingAttr);
    }

    @Test
    void createNewFile150ThenReadAndAppend() {
        final var fileID = FileID.newBuilder().setFileNum(150L).build();
        given(metadata.containsKey(fileID)).willReturn(true);
        given(metadata.get(fileID)).willReturn(livingAttr);
        given(specialFiles.contains(fileID)).willReturn(true);
        given(specialFiles.get(fileID)).willReturn(newContents);

        final var result = subject.overwrite(fileID, newContents);

        assertEquals(SUCCESS, result.outcome());
        assertTrue(result.fileReplaced());
        verify(specialFiles).update(fileID, newContents);

        subject.append(fileID, moreContents);

        verify(specialFiles).append(fileID, moreContents);
    }
}
