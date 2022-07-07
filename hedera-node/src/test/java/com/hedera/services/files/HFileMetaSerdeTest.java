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
package com.hedera.services.files;

import static com.hedera.services.files.HFileMetaSerde.MEMO_VERSION;
import static com.hedera.services.files.HFileMetaSerde.deserialize;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JObjectType;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.SeededPropertySource;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HFileMetaSerdeTest {
    private static final long expiry = 1_234_567L;
    private static final JKey wacl =
            TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asJKeyUnchecked().getKeyList();
    private static final String memo = "Remember me?";
    private static final boolean deleted = true;

    private HFileMeta known;

    @BeforeEach
    void setUp() {
        known = new HFileMeta(deleted, wacl, expiry, memo);
    }

    @Test
    void deserializesWithMemoAndNonNullKey() throws IOException {
        final var propertySource = SeededPropertySource.forSerdeTest((int) MEMO_VERSION, 1);
        final var expected =
                new HFileMeta(
                        propertySource.nextBoolean(),
                        propertySource.nextKeyList(2),
                        propertySource.nextUnsignedLong(),
                        propertySource.nextString(42));

        final var serializedForm = HFileMetaSerde.serialize(expected);

        final var actual =
                HFileMetaSerde.deserialize(
                        new DataInputStream(new ByteArrayInputStream(serializedForm)));

        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    void deserializesWithMemoAndNullKey() throws IOException {
        final var propertySource = SeededPropertySource.forSerdeTest((int) MEMO_VERSION, 2);
        final var expected =
                new HFileMeta(
                        propertySource.nextBoolean(),
                        null,
                        propertySource.nextUnsignedLong(),
                        propertySource.nextString(42));

        final var serializedForm = HFileMetaSerde.serialize(expected);
        final var actual =
                HFileMetaSerde.deserialize(
                        new DataInputStream(new ByteArrayInputStream(serializedForm)));

        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    void legacySerdeTest() throws Exception {
        final var fid = IdUtils.asFile("0.0.1001");
        final var expInfo = toGrpc(known, fid, 1024);
        final var legacyRepr = unhex(hexedLegacyKnown);

        final var replica = deserialize(new DataInputStream(new ByteArrayInputStream(legacyRepr)));
        final var replicaInfo = toGrpc(replica, fid, 1024);

        assertEquals(expInfo.getExpirationTime(), replicaInfo.getExpirationTime());
        assertEquals(expInfo.getDeleted(), replicaInfo.getDeleted());
        assertEquals(expInfo.getKeys().getKeysCount(), replicaInfo.getKeys().getKeysCount());
    }

    @Test
    void throwsOnWrongObjectType() throws IOException {
        final var in = mock(DataInputStream.class);

        given(in.readLong()).willReturn(JObjectType.FC_FILE_INFO.longValue() - 1);

        assertThrows(IllegalStateException.class, () -> HFileMetaSerde.readPreMemoMeta(in));
    }

    private static FileInfo toGrpc(final HFileMeta info, final FileID fid, final long size)
            throws Exception {
        final var expiry = Timestamp.newBuilder().setSeconds(info.getExpiry()).build();

        return FileInfo.newBuilder()
                .setFileID(fid)
                .setSize(size)
                .setExpirationTime(expiry)
                .setDeleted(info.isDeleted())
                .setKeys(JKey.mapJKey(info.getWacl()).getKeyList())
                .build();
    }

    private static final String hexedLegacyKnown =
            "00000000000000010000000000ee994300000000000002e101000000000012d6870000000000000"
                + "0020000000000ecb1f000000000000002c00000000400000000000000020000000000ecf2ea0000"
                + "0000000000209d4eb9cb63c543274d3a15c69335d50c31c948574ef9ae146075a197680502c4000"
                + "00000000000020000000000ecd26d00000000000001380000000100000000000000020000000000"
                + "ecb1f0000000000000011c0000000200000000000000020000000000ecb1f000000000000000c80"
                + "000000200000000000000020000000000ecb1f00000000000000074000000020000000000000002"
                + "0000000000ecf2ea00000000000000204020560eb6a77e8f690eb545dfbbbaee4b516ff8454cf47"
                + "8e3935e2c6b2ff33200000000000000020000000000ecf2ea0000000000000020707d1263ca1070"
                + "2286b02ffdf2b4f6c211ab8d8fe85cde0ac14b2af17efc4a7400000000000000020000000000ecf"
                + "2ea0000000000000020e60fa77b38ed257b294f87f4a62416563530a5decea0f2054f8e86a7c463"
                + "819000000000000000020000000000ecf2ea000000000000002001ddac1c439e1e20819c854b267"
                + "ee3af3e790cd96c44ace40a89ff733501e2ec00000000000000020000000000ecf2ea0000000000"
                + "0000201ebff7723e958d7bf444b42787f5aa9962dd001b368c17cca89a6aea0aa3dae0000000000"
                + "00000020000000000ecb1f000000000000000e40000000100000000000000020000000000ecd26d"
                + "00000000000000c80000000200000000000000020000000000ecb1f000000000000000ac0000000"
                + "300000000000000020000000000ecf2ea0000000000000020ca5667c34cd53224770969525e5cfb"
                + "19c0ad42adfb1e22a981e4ebc68df3947e00000000000000020000000000ecf2ea0000000000000"
                + "020dd8858bde754f7f1f933b749e6fff61163e0a992d89936ad2718bc5b822c0e3e000000000000"
                + "00020000000000ecf2ea000000000000002047c8c60779a621d370686f7b8ae2b670e65d67864da"
                + "b067af7c4407ebeadc7b3";
}
