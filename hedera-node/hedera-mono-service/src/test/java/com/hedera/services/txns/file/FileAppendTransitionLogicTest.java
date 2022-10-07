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
package com.hedera.services.txns.file;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PREPARED_UPDATE_FILE_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;

import com.google.protobuf.ByteString;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.config.MockFileNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.HederaFs;
import com.hedera.services.files.SimpleUpdateResult;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class FileAppendTransitionLogicTest {
    enum TargetType {
        VALID,
        MISSING,
        DELETED,
        IMMUTABLE,
        SPECIAL
    }

    byte[] moreContents = "MORE".getBytes();
    FileID target = IdUtils.asFile("0.0.13257");
    FileID missing = IdUtils.asFile("0.0.75231");
    FileID deleted = IdUtils.asFile("0.0.666");
    FileID immutable = IdUtils.asFile("0.0.667");
    FileID special = IdUtils.asFile("0.0.150");

    HederaFs.UpdateResult success = new SimpleUpdateResult(false, true, SUCCESS);
    HederaFs.UpdateResult okWithCaveat =
            new SimpleUpdateResult(false, true, ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED);

    JKey wacl;
    HFileMeta attr, deletedAttr, immutableAttr;

    TransactionID txnId;
    TransactionBody fileAppendTxn;
    SignedTxnAccessor accessor;

    HederaFs hfs;
    TransactionContext txnCtx;
    SigImpactHistorian sigImpactHistorian;
    MerkleNetworkContext networkCtx;

    FileAppendTransitionLogic subject;
    FileNumbers numbers = new MockFileNumbers();

    @BeforeEach
    void setup() throws Throwable {
        wacl = TxnHandlingScenario.SIMPLE_NEW_WACL_KT.asJKey();
        attr = new HFileMeta(false, wacl, 2_000_000L);
        deletedAttr = new HFileMeta(true, wacl, 2_000_000L);
        immutableAttr = new HFileMeta(false, StateView.EMPTY_WACL, 2_000_000L);

        accessor = mock(SignedTxnAccessor.class);
        txnCtx = mock(TransactionContext.class);
        networkCtx = mock(MerkleNetworkContext.class);
        sigImpactHistorian = mock(SigImpactHistorian.class);

        hfs = mock(HederaFs.class);
        given(hfs.exists(target)).willReturn(true);
        given(hfs.exists(deleted)).willReturn(true);
        given(hfs.exists(special)).willReturn(true);
        given(hfs.exists(immutable)).willReturn(true);
        given(hfs.exists(missing)).willReturn(false);
        given(hfs.getattr(target)).willReturn(attr);
        given(hfs.getattr(deleted)).willReturn(deletedAttr);
        given(hfs.getattr(immutable)).willReturn(immutableAttr);

        subject =
                new FileAppendTransitionLogic(
                        hfs, numbers, txnCtx, sigImpactHistorian, () -> networkCtx);
    }

    @Test
    void resultIsRespected() {
        givenTxnCtxAppending(TargetType.VALID);
        // and:
        given(hfs.append(any(), any())).willReturn(okWithCaveat);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(FEE_SCHEDULE_FILE_PART_UPLOADED);
    }

    @Test
    void setsFailInvalidOnException() {
        givenTxnCtxAppending(TargetType.VALID);
        willThrow(new IllegalStateException("Hmm...")).given(hfs).append(any(), any());

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(FAIL_INVALID);
    }

    @Test
    void catchesOversize() {
        givenTxnCtxAppending(TargetType.VALID);
        given(hfs.append(any(), any()))
                .willThrow(
                        new IllegalArgumentException(
                                TieredHederaFs.IllegalArgumentType.OVERSIZE_CONTENTS.toString()));

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(MAX_FILE_SIZE_EXCEEDED);
    }

    @Test
    void detectsPendingUpdate() {
        givenTxnCtxAppending(TargetType.VALID);
        given(networkCtx.getPreparedUpdateFileNum()).willReturn(target.getFileNum());

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(PREPARED_UPDATE_FILE_IS_IMMUTABLE);
    }

    @Test
    void detectsDeleted() {
        givenTxnCtxAppending(TargetType.DELETED);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(FILE_DELETED);
    }

    @Test
    void detectsImmutable() {
        givenTxnCtxAppending(TargetType.IMMUTABLE);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(UNAUTHORIZED);
    }

    @Test
    void detectsMissing() {
        givenTxnCtxAppending(TargetType.MISSING);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(INVALID_FILE_ID);
    }

    @Test
    void happyPathFlowsForNonSpecialFile() {
        // setup:
        InOrder inOrder = inOrder(hfs, txnCtx);

        givenTxnCtxAppending(TargetType.VALID);
        // and:
        given(hfs.append(any(), any())).willReturn(success);

        // when:
        subject.doStateTransition();

        // then:
        inOrder.verify(hfs)
                .append(
                        argThat(target::equals),
                        argThat(bytes -> Arrays.equals(moreContents, bytes)));
        inOrder.verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void happyPathFlowsForSpecialFile() {
        // setup:
        InOrder inOrder = inOrder(hfs, txnCtx, sigImpactHistorian);

        givenTxnCtxAppending(TargetType.SPECIAL);
        // and:
        given(hfs.append(any(), any())).willReturn(success);

        // when:
        subject.doStateTransition();

        // then:
        inOrder.verify(sigImpactHistorian).markEntityChanged(special.getFileNum());
        inOrder.verify(hfs)
                .append(
                        argThat(special::equals),
                        argThat(bytes -> Arrays.equals(moreContents, bytes)));
        inOrder.verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void syntaxCheckRubberstamps() {
        // given:
        var syntaxCheck = subject.semanticCheck();

        // expect:
        assertEquals(ResponseCodeEnum.OK, syntaxCheck.apply(TransactionBody.getDefaultInstance()));
    }

    @Test
    void hasCorrectApplicability() {
        givenTxnCtxAppending(TargetType.VALID);

        // expect:
        assertTrue(subject.applicability().test(fileAppendTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    private void givenTxnCtxAppending(TargetType type) {
        FileAppendTransactionBody.Builder op = FileAppendTransactionBody.newBuilder();

        switch (type) {
            case IMMUTABLE:
                op.setFileID(immutable);
                break;
            case VALID:
                op.setFileID(target);
                break;
            case MISSING:
                op.setFileID(missing);
                break;
            case DELETED:
                op.setFileID(deleted);
                break;
            case SPECIAL:
                op.setFileID(special);
                break;
        }
        op.setContents(ByteString.copyFrom(moreContents));

        txnId =
                TransactionID.newBuilder()
                        .setTransactionValidStart(
                                MiscUtils.asTimestamp(
                                        Instant.ofEpochSecond(Instant.now().getEpochSecond())))
                        .build();
        fileAppendTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(txnId)
                        .setTransactionValidDuration(Duration.newBuilder().setSeconds(180))
                        .setFileAppend(op)
                        .build();
        given(accessor.getTxn()).willReturn(fileAppendTxn);
        given(txnCtx.accessor()).willReturn(accessor);
    }
}
