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

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileDeleteTransitionLogicTest {
    private static final FileID tbd = IdUtils.asFile("0.0.13257");
    private static final FileID missing = IdUtils.asFile("0.0.75231");
    private static final FileID deleted = IdUtils.asFile("0.0.666");
    private static final FileID immutable = IdUtils.asFile("0.0.667");
    private static final JKey wacl = TxnHandlingScenario.SIMPLE_NEW_WACL_KT.asJKeyUnchecked();
    private static final HFileMeta attr = new HFileMeta(false, wacl, 2_000_000L);
    private static final HFileMeta deletedAttr = new HFileMeta(true, wacl, 2_000_000L);
    private static final HFileMeta immutableAttr =
            new HFileMeta(false, StateView.EMPTY_WACL, 2_000_000L);
    private static final TransactionID txnId =
            TransactionID.newBuilder()
                    .setTransactionValidStart(
                            MiscUtils.asTimestamp(
                                    Instant.ofEpochSecond(Instant.now().getEpochSecond())))
                    .build();

    private TransactionBody fileDeleteTxn;
    private SignedTxnAccessor accessor;
    private HederaFs hfs;
    private TransactionContext txnCtx;
    private SigImpactHistorian sigImpactHistorian;
    private FileDeleteTransitionLogic subject;

    @BeforeEach
    void setup() {
        accessor = mock(SignedTxnAccessor.class);
        txnCtx = mock(TransactionContext.class);
        sigImpactHistorian = mock(SigImpactHistorian.class);

        hfs = mock(HederaFs.class);
        given(hfs.exists(tbd)).willReturn(true);
        given(hfs.exists(deleted)).willReturn(true);
        given(hfs.exists(immutable)).willReturn(true);
        given(hfs.exists(missing)).willReturn(false);
        given(hfs.getattr(tbd)).willReturn(attr);
        given(hfs.getattr(deleted)).willReturn(deletedAttr);
        given(hfs.getattr(immutable)).willReturn(immutableAttr);

        subject = new FileDeleteTransitionLogic(hfs, sigImpactHistorian, txnCtx);
    }

    @Test
    void happyPathFlows() {
        final var inOrder = inOrder(hfs, txnCtx, accessor, sigImpactHistorian);
        givenTxnCtxDeleting(TargetType.VALID);

        subject.doStateTransition();

        inOrder.verify(txnCtx).accessor();
        inOrder.verify(accessor).getTxn();
        inOrder.verify(hfs).exists(tbd);
        inOrder.verify(hfs).getattr(tbd);
        inOrder.verify(hfs).delete(tbd);
        inOrder.verify(sigImpactHistorian).markEntityChanged(tbd.getFileNum());
    }

    @Test
    void detectsDeleted() {
        givenTxnCtxDeleting(TargetType.DELETED);

        assertFailsWith(() -> subject.doStateTransition(), FILE_DELETED);

        verify(hfs, never()).delete(any());
    }

    @Test
    void detectsMissing() {
        givenTxnCtxDeleting(TargetType.MISSING);

        assertFailsWith(() -> subject.doStateTransition(), INVALID_FILE_ID);

        verify(hfs, never()).delete(any());
    }

    @Test
    void resultIsRespected() {
        givenTxnCtxDeleting(TargetType.VALID);

        doThrow(new InvalidTransactionException(ENTITY_NOT_ALLOWED_TO_DELETE))
                .when(hfs)
                .delete(any());

        assertFailsWith(() -> subject.doStateTransition(), ENTITY_NOT_ALLOWED_TO_DELETE);
    }

    @Test
    void rejectsImmutableTarget() {
        givenTxnCtxDeleting(TargetType.IMMUTABLE);

        assertFailsWith(() -> subject.doStateTransition(), UNAUTHORIZED);

        verify(hfs, never()).delete(any());
    }

    @Test
    void hasCorrectApplicability() {
        givenTxnCtxDeleting(TargetType.VALID);

        assertTrue(subject.applicability().test(fileDeleteTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void syntaxCheckRubberstamps() {
        final var syntaxCheck = subject.semanticCheck();

        assertEquals(OK, syntaxCheck.apply(TransactionBody.getDefaultInstance()));
    }

    private void givenTxnCtxDeleting(final TargetType type) {
        final var op = FileDeleteTransactionBody.newBuilder();

        switch (type) {
            case IMMUTABLE:
                op.setFileID(immutable);
                break;
            case VALID:
                op.setFileID(tbd);
                break;
            case MISSING:
                op.setFileID(missing);
                break;
            case DELETED:
                op.setFileID(deleted);
                break;
        }

        fileDeleteTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(txnId)
                        .setTransactionValidDuration(Duration.newBuilder().setSeconds(180))
                        .setFileDelete(op)
                        .build();
        given(accessor.getTxn()).willReturn(fileDeleteTxn);
        given(txnCtx.accessor()).willReturn(accessor);
    }

    private enum TargetType {
        IMMUTABLE,
        VALID,
        MISSING,
        DELETED
    }
}
