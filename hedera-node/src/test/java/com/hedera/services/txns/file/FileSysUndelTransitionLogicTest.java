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
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class FileSysUndelTransitionLogicTest {
    enum TargetType {
        VALID,
        MISSING,
        DELETED
    }

    enum OldExpiryType {
        NONE,
        FUTURE,
        PAST
    }

    long now = Instant.now().getEpochSecond();
    long lifetime = 1_000_000L;
    long currExpiry = now + lifetime / 2;
    long oldPastExpiry = now - 1;
    long oldFutureExpiry = now + lifetime;

    FileID undeleted = IdUtils.asFile("0.0.13257");
    FileID missing = IdUtils.asFile("0.0.75231");
    FileID deleted = IdUtils.asFile("0.0.666");

    HederaFs.UpdateResult success = new SimpleUpdateResult(true, false, SUCCESS);

    JKey wacl;
    HFileMeta attr, deletedAttr;

    TransactionID txnId;
    TransactionBody fileSysUndelTxn;
    SignedTxnAccessor accessor;

    HederaFs hfs;
    SigImpactHistorian sigImpactHistorian;
    Map<EntityId, Long> oldExpiries;
    TransactionContext txnCtx;
    PropertySource properties;

    FileSysUndelTransitionLogic subject;

    @BeforeEach
    void setup() throws Throwable {
        wacl = TxnHandlingScenario.SIMPLE_NEW_WACL_KT.asJKey();
        attr = new HFileMeta(false, wacl, currExpiry);
        deletedAttr = new HFileMeta(true, wacl, currExpiry);

        accessor = mock(SignedTxnAccessor.class);
        txnCtx = mock(TransactionContext.class);
        oldExpiries = mock(Map.class);
        sigImpactHistorian = mock(SigImpactHistorian.class);
        properties = mock(PropertySource.class);

        hfs = mock(HederaFs.class);
        given(hfs.exists(undeleted)).willReturn(true);
        given(hfs.exists(deleted)).willReturn(true);
        given(hfs.exists(missing)).willReturn(false);
        given(hfs.getattr(undeleted)).willReturn(attr);
        given(hfs.getattr(deleted)).willReturn(deletedAttr);
        given(properties.getTypesProperty(ENTITIES_SYSTEM_DELETABLE))
                .willReturn(EnumSet.allOf(EntityType.class));

        subject =
                new FileSysUndelTransitionLogic(
                        hfs, sigImpactHistorian, oldExpiries, txnCtx, properties);
    }

    @Test
    void abortsIfNotSupported() {
        given(properties.getTypesProperty(ENTITIES_SYSTEM_DELETABLE))
                .willReturn(EnumSet.noneOf(EntityType.class));

        subject =
                new FileSysUndelTransitionLogic(
                        hfs, sigImpactHistorian, oldExpiries, txnCtx, properties);

        subject.doStateTransition();
        verify(txnCtx).setStatus(NOT_SUPPORTED);
        verifyNoInteractions(hfs);
    }

    @Test
    void happyPathFlows() {
        // setup:
        InOrder inOrder = inOrder(hfs, txnCtx, oldExpiries, sigImpactHistorian);

        givenTxnCtxSysUndeleting(TargetType.DELETED, OldExpiryType.FUTURE);
        // and:
        given(hfs.sudoSetattr(any(), any())).willReturn(success);

        // when:
        subject.doStateTransition();

        // then:
        assertFalse(deletedAttr.isDeleted());
        assertEquals(oldFutureExpiry, deletedAttr.getExpiry());
        inOrder.verify(hfs).sudoSetattr(deleted, deletedAttr);
        inOrder.verify(oldExpiries).remove(EntityId.fromGrpcFileId(deleted));
        inOrder.verify(txnCtx).setStatus(SUCCESS);
        inOrder.verify(sigImpactHistorian).markEntityChanged(deleted.getFileNum());
    }

    @Test
    void destroysIfOldExpiryIsPast() {
        givenTxnCtxSysUndeleting(TargetType.DELETED, OldExpiryType.PAST);

        // when:
        subject.doStateTransition();

        // then:
        verify(hfs).rm(deleted);
        verify(oldExpiries).remove(EntityId.fromGrpcFileId(deleted));
        verify(hfs, never()).sudoSetattr(any(), any());
    }

    @Test
    void detectsUndeleted() {
        givenTxnCtxSysUndeleting(TargetType.VALID, OldExpiryType.FUTURE);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(INVALID_FILE_ID);
    }

    @Test
    void detectsMissing() {
        givenTxnCtxSysUndeleting(TargetType.MISSING, OldExpiryType.FUTURE);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(INVALID_FILE_ID);
    }

    @Test
    void detectsUserDeleted() {
        givenTxnCtxSysUndeleting(TargetType.DELETED, OldExpiryType.NONE);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(INVALID_FILE_ID);
    }

    @Test
    void hasCorrectApplicability() {
        // setup:
        SystemUndeleteTransactionBody.Builder op =
                SystemUndeleteTransactionBody.newBuilder()
                        .setContractID(IdUtils.asContract("0.0.1001"));
        var contractSysUndelTxn = TransactionBody.newBuilder().setSystemUndelete(op).build();

        givenTxnCtxSysUndeleting(TargetType.VALID, OldExpiryType.FUTURE);

        // expect:
        assertTrue(subject.applicability().test(fileSysUndelTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
        assertFalse(subject.applicability().test(contractSysUndelTxn));
    }

    @Test
    void syntaxCheckRubberstamps() {
        // given:
        var syntaxCheck = subject.semanticCheck();

        // expect:
        assertEquals(ResponseCodeEnum.OK, syntaxCheck.apply(TransactionBody.getDefaultInstance()));
    }

    private void givenTxnCtxSysUndeleting(TargetType type, OldExpiryType expiryType) {
        SystemUndeleteTransactionBody.Builder op = SystemUndeleteTransactionBody.newBuilder();

        FileID id = null;
        switch (type) {
            case VALID:
                op.setFileID(undeleted);
                id = undeleted;
                break;
            case MISSING:
                op.setFileID(missing);
                id = missing;
                break;
            case DELETED:
                op.setFileID(deleted);
                id = deleted;
                break;
        }
        EntityId entity = EntityId.fromGrpcFileId(id);

        switch (expiryType) {
            case NONE:
                given(oldExpiries.containsKey(entity)).willReturn(false);
                given(oldExpiries.get(entity)).willReturn(null);
                break;
            case FUTURE:
                given(oldExpiries.containsKey(entity)).willReturn(true);
                given(oldExpiries.get(entity)).willReturn(oldFutureExpiry);
                break;
            case PAST:
                given(oldExpiries.containsKey(entity)).willReturn(true);
                given(oldExpiries.get(entity)).willReturn(oldPastExpiry);
                break;
        }

        txnId =
                TransactionID.newBuilder()
                        .setTransactionValidStart(
                                MiscUtils.asTimestamp(
                                        Instant.ofEpochSecond(Instant.now().getEpochSecond())))
                        .build();
        fileSysUndelTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(txnId)
                        .setTransactionValidDuration(Duration.newBuilder().setSeconds(180))
                        .setSystemUndelete(op)
                        .build();
        given(accessor.getTxn()).willReturn(fileSysUndelTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
    }
}
