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
package com.hedera.services.keys;

import static com.hedera.services.keys.DefaultActivationCharacteristics.DEFAULT_ACTIVATION_CHARACTERISTICS;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.HederaFs;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CharacteristicsFactoryTest {
    FileID target = IdUtils.asFile("0.0.75231");
    FileID missing = IdUtils.asFile("1.2.3");
    JKeyList wacl = new JKeyList(List.of(new JEd25519Key("NOPE".getBytes())));
    HFileMeta info = new HFileMeta(false, wacl, 1_234_567L);

    HederaFs hfs;
    CharacteristicsFactory subject;
    KeyActivationCharacteristics revocationServiceCharacteristics;
    Function<JKeyList, KeyActivationCharacteristics> revocationServiceCharacteristicsFn;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        hfs = mock(HederaFs.class);
        given(hfs.exists(target)).willReturn(true);
        given(hfs.getattr(target)).willReturn(info);

        revocationServiceCharacteristics = mock(KeyActivationCharacteristics.class);
        revocationServiceCharacteristicsFn =
                (Function<JKeyList, KeyActivationCharacteristics>) mock(Function.class);
        given(revocationServiceCharacteristicsFn.apply(wacl))
                .willReturn(revocationServiceCharacteristics);

        subject = new CharacteristicsFactory(hfs);

        CharacteristicsFactory.revocationServiceCharacteristicsFn =
                revocationServiceCharacteristicsFn;
    }

    @AfterEach
    void cleanup() {
        CharacteristicsFactory.revocationServiceCharacteristicsFn =
                RevocationServiceCharacteristics::forTopLevelFile;
    }

    @Test
    void usesDefaultForNonFileDelete() {
        // expect:
        assertSame(DEFAULT_ACTIVATION_CHARACTERISTICS, subject.inferredFor(nonFileDelete()));
    }

    @Test
    void usesDefaultForMalformedFileDelete() {
        // expect:
        assertSame(
                DEFAULT_ACTIVATION_CHARACTERISTICS, subject.inferredFor(meaninglessFileDelete()));
        assertSame(DEFAULT_ACTIVATION_CHARACTERISTICS, subject.inferredFor(missingFileDelete()));
    }

    @Test
    void usesAproposForFileDelete() {
        // expect:
        assertSame(revocationServiceCharacteristics, subject.inferredFor(fileDelete()));
    }

    private TransactionBody nonFileDelete() {
        return TransactionBody.newBuilder()
                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                .build();
    }

    private TransactionBody meaninglessFileDelete() {
        return TransactionBody.newBuilder()
                .setFileDelete(FileDeleteTransactionBody.getDefaultInstance())
                .build();
    }

    private TransactionBody missingFileDelete() {
        return TransactionBody.newBuilder()
                .setFileDelete(FileDeleteTransactionBody.newBuilder().setFileID(missing))
                .build();
    }

    private TransactionBody fileDelete() {
        return TransactionBody.newBuilder()
                .setFileDelete(FileDeleteTransactionBody.newBuilder().setFileID(target))
                .build();
    }
}
