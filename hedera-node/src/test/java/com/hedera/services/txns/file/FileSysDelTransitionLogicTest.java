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

import static com.hedera.services.context.properties.PropertyNames.ENTITIES_SYSTEM_DELETABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.EntityType;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.HederaFs;
import com.hedera.services.files.SimpleUpdateResult;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class FileSysDelTransitionLogicTest {
    enum TargetType {
        VALID,
        MISSING,
        DELETED
    }

    enum NewExpiryType {
        NONE,
        FUTURE,
        PAST
    }

    long now = Instant.now().getEpochSecond();
    long lifetime = 1_000_000L;
    long oldExpiry = now + lifetime;
    long newExpiry = now + lifetime / 2;

    FileID tbd = IdUtils.asFile("0.0.13257");
    FileID missing = IdUtils.asFile("0.0.75231");
    FileID deleted = IdUtils.asFile("0.0.666");

    HederaFs.UpdateResult success = new SimpleUpdateResult(true, false, SUCCESS);

    JKey wacl;
    HFileMeta attr, deletedAttr;

    TransactionID txnId;
    TransactionBody fileSysDelTxn;
    SignedTxnAccessor accessor;

    HederaFs hfs;
    Map<EntityId, Long> oldExpiries;
    TransactionContext txnCtx;
    SigImpactHistorian sigImpactHistorian;
    PropertySource properties;

    FileSysDelTransitionLogic subject;

    @BeforeEach
    void setup() throws Throwable {
        wacl = TxnHandlingScenario.SIMPLE_NEW_WACL_KT.asJKey();
        attr = new HFileMeta(false, wacl, oldExpiry);
        deletedAttr = new HFileMeta(true, wacl, oldExpiry);

        accessor = mock(SignedTxnAccessor.class);
        txnCtx = mock(TransactionContext.class);
        sigImpactHistorian = mock(SigImpactHistorian.class);
        oldExpiries = mock(Map.class);
        properties = mock(PropertySource.class);

        hfs = mock(HederaFs.class);
        given(hfs.exists(tbd)).willReturn(true);
        given(hfs.exists(deleted)).willReturn(true);
        given(hfs.exists(missing)).willReturn(false);
        given(hfs.getattr(tbd)).willReturn(attr);
        given(hfs.getattr(deleted)).willReturn(deletedAttr);
        given(properties.getTypesProperty(ENTITIES_SYSTEM_DELETABLE))
                .willReturn(EnumSet.allOf(EntityType.class));

        subject =
                new FileSysDelTransitionLogic(
                        hfs, sigImpactHistorian, oldExpiries, txnCtx, properties);
    }

    @Test
    void abortsIfNotSupported() {
        given(properties.getTypesProperty(ENTITIES_SYSTEM_DELETABLE))
                .willReturn(EnumSet.noneOf(EntityType.class));

        subject =
                new FileSysDelTransitionLogic(
                        hfs, sigImpactHistorian, oldExpiries, txnCtx, properties);

        subject.doStateTransition();
        verify(txnCtx).setStatus(NOT_SUPPORTED);
        verifyNoInteractions(hfs);
    }

    @Test
    void destroysIfNewExpiryIsPast() {
        givenTxnCtxSysDeleting(TargetType.VALID, NewExpiryType.PAST);

        // when:
        subject.doStateTransition();

        // then:
        verify(hfs).rm(tbd);
        verify(oldExpiries, never()).put(any(), any());
    }

    @Test
    void leavesUnspecifiedExpiryUntouched() {
        // setup:
        InOrder inOrder = inOrder(hfs, txnCtx, oldExpiries);

        givenTxnCtxSysDeleting(TargetType.VALID, NewExpiryType.NONE);
        // and:
        given(hfs.setattr(any(), any())).willReturn(success);

        // when:
        subject.doStateTransition();

        // then:
        assertTrue(attr.isDeleted());
        ;
        assertEquals(oldExpiry, attr.getExpiry());
        inOrder.verify(hfs).setattr(tbd, attr);
        inOrder.verify(oldExpiries).put(EntityId.fromGrpcFileId(tbd), Long.valueOf(oldExpiry));
        inOrder.verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void setsFailInvalidOnException() {
        givenTxnCtxSysDeleting(TargetType.VALID, NewExpiryType.PAST);
        willThrow(new IllegalStateException("Hmm...")).given(hfs).rm(any());

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(FAIL_INVALID);
    }

    @Test
    void happyPathFlows() {
        // setup:
        InOrder inOrder = inOrder(hfs, txnCtx, oldExpiries, sigImpactHistorian);

        givenTxnCtxSysDeleting(TargetType.VALID, NewExpiryType.FUTURE);
        // and:
        given(hfs.setattr(any(), any())).willReturn(success);

        // when:
        subject.doStateTransition();

        // then:
        assertTrue(attr.isDeleted());
        assertEquals(newExpiry, attr.getExpiry());
        inOrder.verify(hfs).setattr(tbd, attr);
        inOrder.verify(oldExpiries).put(EntityId.fromGrpcFileId(tbd), oldExpiry);
        inOrder.verify(txnCtx).setStatus(SUCCESS);
        inOrder.verify(sigImpactHistorian).markEntityChanged(tbd.getFileNum());
    }

    @Test
    void detectsDeleted() {
        givenTxnCtxSysDeleting(TargetType.DELETED, NewExpiryType.FUTURE);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(FILE_DELETED);
    }

    @Test
    void detectsMissing() {
        givenTxnCtxSysDeleting(TargetType.MISSING, NewExpiryType.FUTURE);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(INVALID_FILE_ID);
    }

    @Test
    void hasCorrectApplicability() {
        // setup:
        SystemDeleteTransactionBody.Builder op =
                SystemDeleteTransactionBody.newBuilder()
                        .setContractID(IdUtils.asContract("0.0.1001"));
        var contractSysdelTxn = TransactionBody.newBuilder().setSystemDelete(op).build();

        givenTxnCtxSysDeleting(TargetType.VALID, NewExpiryType.FUTURE);

        // expect:
        assertTrue(subject.applicability().test(fileSysDelTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
        assertFalse(subject.applicability().test(contractSysdelTxn));
    }

    @Test
    void syntaxCheckRubberstamps() {
        // given:
        var syntaxCheck = subject.semanticCheck();

        // expect:
        assertEquals(ResponseCodeEnum.OK, syntaxCheck.apply(TransactionBody.getDefaultInstance()));
    }

    private void givenTxnCtxSysDeleting(TargetType type, NewExpiryType expiryType) {
        SystemDeleteTransactionBody.Builder op = SystemDeleteTransactionBody.newBuilder();

        switch (type) {
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

        switch (expiryType) {
            case NONE:
                break;
            case FUTURE:
                op.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(newExpiry));
                break;
            case PAST:
                op.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(now - 1));
                break;
        }

        txnId =
                TransactionID.newBuilder()
                        .setTransactionValidStart(
                                MiscUtils.asTimestamp(
                                        Instant.ofEpochSecond(Instant.now().getEpochSecond())))
                        .build();
        fileSysDelTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(txnId)
                        .setTransactionValidDuration(Duration.newBuilder().setSeconds(180))
                        .setSystemDelete(op)
                        .build();
        given(accessor.getTxn()).willReturn(fileSysDelTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
    }
}
