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
package com.hedera.services.txns.contract;

import static com.hedera.services.context.properties.EntityType.CONTRACT;
import static com.hedera.services.context.properties.PropertyNames.ENTITIES_SYSTEM_DELETABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.EntityType;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContractSysUndelTransitionLogicTest {
    private final AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
    private final ContractID target = ContractID.newBuilder().setContractNum(9_999L).build();

    private Instant consensusTime;
    private OptionValidator validator;
    private ContractSysUndelTransitionLogic.LegacySystemUndeleter delegate;
    private TransactionBody contractSysUndelTxn;
    private TransactionContext txnCtx;
    private SignedTxnAccessor accessor;
    private SigImpactHistorian sigImpactHistorian;
    MerkleMap<EntityNum, MerkleAccount> contracts;
    PropertySource properties;

    ContractSysUndelTransitionLogic subject;

    @BeforeEach
    void setup() {
        consensusTime = Instant.now();

        delegate = mock(ContractSysUndelTransitionLogic.LegacySystemUndeleter.class);
        txnCtx = mock(TransactionContext.class);
        given(txnCtx.consensusTime()).willReturn(consensusTime);
        accessor = mock(SignedTxnAccessor.class);
        validator = mock(OptionValidator.class);
        withRubberstampingValidator();
        sigImpactHistorian = mock(SigImpactHistorian.class);
        properties = mock(PropertySource.class);
        given(properties.getTypesProperty(ENTITIES_SYSTEM_DELETABLE))
                .willReturn(EnumSet.of(CONTRACT));

        subject =
                new ContractSysUndelTransitionLogic(
                        validator,
                        sigImpactHistorian,
                        txnCtx,
                        delegate,
                        () -> contracts,
                        properties);
    }

    @Test
    void abortsIfNotSupported() {
        givenValidTxnCtx();
        given(properties.getTypesProperty(ENTITIES_SYSTEM_DELETABLE))
                .willReturn(EnumSet.of(EntityType.TOKEN));

        subject =
                new ContractSysUndelTransitionLogic(
                        validator,
                        sigImpactHistorian,
                        txnCtx,
                        delegate,
                        () -> contracts,
                        properties);

        assertEquals(NOT_SUPPORTED, subject.validate(contractSysUndelTxn));
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(contractSysUndelTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void capturesBadUndelete() {
        // setup:
        TransactionRecord sysUndelRec =
                TransactionRecord.newBuilder()
                        .setReceipt(
                                TransactionReceipt.newBuilder()
                                        .setStatus(INVALID_CONTRACT_ID)
                                        .build())
                        .build();

        givenValidTxnCtx();
        // and:
        given(delegate.perform(contractSysUndelTxn, consensusTime)).willReturn(sysUndelRec);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(INVALID_CONTRACT_ID);
    }

    @Test
    void followsHappyPathWithOverrides() {
        // setup:
        TransactionRecord sysUndelRec =
                TransactionRecord.newBuilder()
                        .setReceipt(TransactionReceipt.newBuilder().setStatus(SUCCESS).build())
                        .build();

        givenValidTxnCtx();
        // and:
        given(delegate.perform(contractSysUndelTxn, consensusTime)).willReturn(sysUndelRec);

        // when:
        subject.doStateTransition();

        // then:
        verify(sigImpactHistorian).markEntityChanged(target.getContractNum());
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void acceptsOkSyntax() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(contractSysUndelTxn));
    }

    @Test
    void acceptsDeletedContract() {
        givenValidTxnCtx();
        // and:
        given(validator.queryableContractStatus(target, contracts)).willReturn(CONTRACT_DELETED);

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(contractSysUndelTxn));
    }

    @Test
    void rejectsInvalidCid() {
        givenValidTxnCtx();
        // and:
        given(validator.queryableContractStatus(target, contracts)).willReturn(INVALID_CONTRACT_ID);

        // expect:
        assertEquals(INVALID_CONTRACT_ID, subject.semanticCheck().apply(contractSysUndelTxn));
    }

    @Test
    void translatesUnknownException() {
        givenValidTxnCtx();

        given(delegate.perform(any(), any())).willThrow(IllegalStateException.class);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(FAIL_INVALID);
    }

    private void givenValidTxnCtx() {
        var op =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setSystemUndelete(
                                SystemUndeleteTransactionBody.newBuilder().setContractID(target));
        contractSysUndelTxn = op.build();
        given(accessor.getTxn()).willReturn(contractSysUndelTxn);
        given(txnCtx.accessor()).willReturn(accessor);
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(payer)
                .setTransactionValidStart(
                        Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
                .build();
    }

    private void withRubberstampingValidator() {
        given(validator.queryableContractStatus(target, contracts)).willReturn(OK);
    }
}
