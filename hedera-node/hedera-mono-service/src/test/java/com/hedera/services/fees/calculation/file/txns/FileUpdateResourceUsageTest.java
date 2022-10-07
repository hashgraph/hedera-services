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
package com.hedera.services.fees.calculation.file.txns;

import static com.hedera.services.state.merkle.MerkleAccountState.DEFAULT_MEMO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.usage.file.ExtantFileContext;
import com.hedera.services.usage.file.FileOpsUsage;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.SigValueObj;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FileUpdateResourceUsageTest {
    long now = 1_000_000L;

    KeyList wacl = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey().getKeyList();
    String memo = "Certainly not!";
    long expiry = 1_234_567L;
    long size = 1L;

    long newExpiry = 2_345_678L;

    int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
    SigValueObj svo = new SigValueObj(numSigs, numPayerKeys, sigsSize);
    private FileOpsUsage fileOpsUsage;

    private FileUpdateResourceUsage subject;

    StateView view;
    FileID fid = IdUtils.asFile("1.2.3");
    FeeData expected;

    private TransactionBody nonFileUpdateTxn;
    private TransactionBody fileUpdateTxn;

    @BeforeEach
    void setup() throws Throwable {
        fileOpsUsage = mock(FileOpsUsage.class);

        view = mock(StateView.class);

        subject = new FileUpdateResourceUsage(fileOpsUsage);
    }

    @Test
    void recognizesApplicability() {
        fileUpdateTxn = mock(TransactionBody.class);
        given(fileUpdateTxn.hasFileUpdate()).willReturn(true);

        nonFileUpdateTxn = mock(TransactionBody.class);
        given(nonFileUpdateTxn.hasFileUpdate()).willReturn(false);

        // expect:
        assertTrue(subject.applicableTo(fileUpdateTxn));
        assertFalse(subject.applicableTo(nonFileUpdateTxn));
    }

    @Test
    void missingCtxScans() {
        // setup:
        long now = 1_234_567L;

        // given:
        var ctx = FileUpdateResourceUsage.missingCtx(now);

        // expect:
        assertEquals(0, ctx.currentSize());
        assertEquals(now, ctx.currentExpiry());
        Assertions.assertSame(KeyList.getDefaultInstance(), ctx.currentWacl());
        Assertions.assertSame(DEFAULT_MEMO, ctx.currentMemo());
    }

    @Test
    void delegatesToCorrectEstimateWhenUnknown() throws Exception {
        // setup:
        expected = mock(FeeData.class);
        // and:
        ArgumentCaptor<ExtantFileContext> captor = ArgumentCaptor.forClass(ExtantFileContext.class);

        given(fileOpsUsage.fileUpdateUsage(any(), any(), captor.capture())).willReturn(expected);
        given(view.infoForFile(fid)).willReturn(Optional.empty());

        // when:
        fileUpdateTxn = txnAt(now);
        var actual = subject.usageGiven(fileUpdateTxn, svo, view);

        // then:
        assertSame(expected, actual);
        // and:
        var ctxUsed = captor.getValue();
        assertEquals(now, ctxUsed.currentExpiry());
    }

    @Test
    void delegatesToCorrectEstimateWhenKnown() throws Exception {
        // setup:
        expected = mock(FeeData.class);
        // and:
        var info =
                FileGetInfoResponse.FileInfo.newBuilder()
                        .setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
                        .setMemo(memo)
                        .setKeys(wacl)
                        .setSize(size)
                        .build();
        // and:
        ArgumentCaptor<ExtantFileContext> captor = ArgumentCaptor.forClass(ExtantFileContext.class);

        given(fileOpsUsage.fileUpdateUsage(any(), any(), captor.capture())).willReturn(expected);
        given(view.infoForFile(fid)).willReturn(Optional.of(info));

        // when:
        fileUpdateTxn = txnAt(now);
        var actual = subject.usageGiven(fileUpdateTxn, svo, view);

        // then:
        assertSame(expected, actual);
        // and:
        var ctxUsed = captor.getValue();
        assertEquals(expiry, ctxUsed.currentExpiry());
        assertEquals(memo, ctxUsed.currentMemo());
        assertEquals(wacl, ctxUsed.currentWacl());
        assertEquals(size, ctxUsed.currentSize());
    }

    private TransactionBody txnAt(long now) {
        var op =
                FileUpdateTransactionBody.newBuilder()
                        .setFileID(fid)
                        .setContents(
                                ByteString.copyFrom(
                                        "Though like waves breaking it may be".getBytes()))
                        .setKeys(
                                KeyList.newBuilder()
                                        .addKeys(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey())
                                        .addKeys(TxnHandlingScenario.MISC_ACCOUNT_KT.asKey())
                                        .build())
                        .setExpirationTime(Timestamp.newBuilder().setSeconds(newExpiry))
                        .build();
        // and:
        return TransactionBody.newBuilder()
                .setTransactionID(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setFileUpdate(op)
                .build();
    }
}
