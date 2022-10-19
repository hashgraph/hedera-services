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

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.STAKING_NOT_ENABLED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.txns.contract.helpers.UpdateCustomizerFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContractUpdateTransitionLogicTest {
    private static final ByteString pretendAlias = ByteString.copyFromUtf8("abcd");
    private final AccountID proxy = AccountID.newBuilder().setAccountNum(4_321L).build();
    private final AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
    private final ContractID target = ContractID.newBuilder().setContractNum(9_999L).build();
    private final AccountID targetId = AccountID.newBuilder().setAccountNum(9_999L).build();
    private final String memo = "Who, me?";
    private final int maxAutoAssociations = NEW_MAX_AUTOMATIC_ASSOCIATIONS;
    private static final int CUR_MAX_AUTOMATIC_ASSOCIATIONS = 10;
    private static final int NEW_MAX_AUTOMATIC_ASSOCIATIONS = 15;

    private long customAutoRenewPeriod = 100_001L;

    private Instant consensusTime;
    private HederaLedger ledger;
    private AliasManager aliasManager;
    private MerkleAccount contract = new MerkleAccount();
    private OptionValidator validator;
    private SigImpactHistorian sigImpactHistorian;
    private HederaAccountCustomizer customizer;
    private UpdateCustomizerFactory customizerFactory;
    private TransactionBody contractUpdateTxn;
    private TransactionContext txnCtx;
    private SignedTxnAccessor accessor;
    private MerkleMap<EntityNum, MerkleAccount> contracts;
    private GlobalDynamicProperties dynamicProperties;
    private ContractUpdateTransitionLogic subject;
    private NodeInfo nodeInfo;

    @BeforeEach
    void setup() {
        consensusTime = Instant.now();

        ledger = mock(HederaLedger.class);
        contracts = (MerkleMap<EntityNum, MerkleAccount>) mock(MerkleMap.class);
        customizerFactory = mock(UpdateCustomizerFactory.class);
        txnCtx = mock(TransactionContext.class);
        given(txnCtx.consensusTime()).willReturn(consensusTime);
        accessor = mock(SignedTxnAccessor.class);
        validator = mock(OptionValidator.class);
        withRubberstampingValidator();
        sigImpactHistorian = mock(SigImpactHistorian.class);
        aliasManager = mock(AliasManager.class);
        dynamicProperties = mock(GlobalDynamicProperties.class);

        nodeInfo = mock(NodeInfo.class);

        subject =
                new ContractUpdateTransitionLogic(
                        ledger,
                        aliasManager,
                        validator,
                        sigImpactHistorian,
                        txnCtx,
                        customizerFactory,
                        () -> AccountStorageAdapter.fromInMemory(contracts),
                        dynamicProperties,
                        nodeInfo);
    }

    @Test
    void abortsIfCustomizerUnhappy() {
        // setup:
        customizer = mock(HederaAccountCustomizer.class);

        givenValidTxnCtx();
        given(
                        customizerFactory.customizerFor(
                                contract, validator, contractUpdateTxn.getContractUpdateInstance()))
                .willReturn(Pair.of(Optional.empty(), MODIFYING_IMMUTABLE_CONTRACT));

        // when:
        subject.doStateTransition();

        // then:
        verify(ledger, never()).customize(targetId, customizer);
        verify(txnCtx).setStatus(MODIFYING_IMMUTABLE_CONTRACT);
    }

    @Test
    void runsHappyPath() {
        // setup:
        customizer = mock(HederaAccountCustomizer.class);

        givenValidTxnCtx();
        given(
                        customizerFactory.customizerFor(
                                contract, validator, contractUpdateTxn.getContractUpdateInstance()))
                .willReturn(Pair.of(Optional.of(customizer), OK));

        // when:
        subject.doStateTransition();

        // then:
        verify(ledger).customize(targetId, customizer);
        verify(txnCtx).setTargetedContract(target);
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(target.getContractNum());
    }

    @Test
    void runsHappyPathWithAlias() {
        // setup:
        customizer = mock(HederaAccountCustomizer.class);

        givenValidTxnCtx();
        given(
                        customizerFactory.customizerFor(
                                contract, validator, contractUpdateTxn.getContractUpdateInstance()))
                .willReturn(Pair.of(Optional.of(customizer), OK));
        contract.setAlias(pretendAlias);

        // when:
        subject.doStateTransition();

        // then:
        verify(ledger).customize(targetId, customizer);
        verify(txnCtx).setStatus(SUCCESS);
        verify(sigImpactHistorian).markEntityChanged(target.getContractNum());
        verify(sigImpactHistorian).markAliasChanged(pretendAlias);
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(contractUpdateTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void rejectsInvalidMemoInSyntaxCheck() {
        givenValidTxnCtx();
        // and:
        given(validator.memoCheck(any())).willReturn(INVALID_ZERO_BYTE_IN_STRING);

        // expect:
        assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.semanticCheck().apply(contractUpdateTxn));
    }

    @Test
    void acceptsOkSyntax() {
        givenValidTxnCtx();
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(contractUpdateTxn));
    }

    @Test
    void acceptsOmittedAutoRenew() {
        givenValidTxnCtx(false, false, false, false);
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(true);

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(contractUpdateTxn));
    }

    @Test
    void rejectsInvalidCid() {
        givenValidTxnCtx();
        // and:
        given(validator.queryableContractStatus(eq(EntityNum.fromContractId(target)), any()))
                .willReturn(CONTRACT_DELETED);

        // expect:
        assertEquals(CONTRACT_DELETED, subject.semanticCheck().apply(contractUpdateTxn));
    }

    @Test
    void rejectsInvalidAutoRenew() {
        // setup:
        customAutoRenewPeriod = -1;

        givenValidTxnCtx();

        // expect:
        assertEquals(INVALID_RENEWAL_PERIOD, subject.semanticCheck().apply(contractUpdateTxn));
    }

    @Test
    void rejectsOutOfRangeAutoRenew() {
        givenValidTxnCtx();
        // and:
        given(validator.isValidAutoRenewPeriod(any())).willReturn(false);

        // expect:
        assertEquals(
                AUTORENEW_DURATION_NOT_IN_RANGE, subject.semanticCheck().apply(contractUpdateTxn));
    }

    @Test
    void failsForDeclineRewardIfStakingDisabled() {
        givenValidTxnCtx(true, false, false, true);

        assertEquals(STAKING_NOT_ENABLED, subject.semanticCheck().apply(contractUpdateTxn));
    }

    @Test
    void failsForInvalidStakingId() {
        givenValidTxnCtxWithStaking();

        assertEquals(STAKING_NOT_ENABLED, subject.semanticCheck().apply(contractUpdateTxn));
        given(dynamicProperties.isStakingEnabled()).willReturn(true);

        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(false);
        assertEquals(INVALID_STAKING_ID, subject.semanticCheck().apply(contractUpdateTxn));

        contractUpdateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setContractUpdateInstance(
                                ContractUpdateTransactionBody.newBuilder()
                                        .setMemo(memo)
                                        .setContractID(target)
                                        .setStakedAccountId(asAccount("0.0.0")))
                        .build();
        assertEquals(OK, subject.semanticCheck().apply(contractUpdateTxn));

        contractUpdateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setContractUpdateInstance(
                                ContractUpdateTransactionBody.newBuilder()
                                        .setMemo(memo)
                                        .setContractID(target)
                                        .setStakedNodeId(-1L))
                        .build();
        assertEquals(OK, subject.semanticCheck().apply(contractUpdateTxn));

        contractUpdateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setContractUpdateInstance(
                                ContractUpdateTransactionBody.newBuilder()
                                        .setMemo(memo)
                                        .setContractID(target)
                                        .setStakedAccountId(asAccount("0.0.-1")))
                        .build();
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(false);
        assertEquals(INVALID_STAKING_ID, subject.semanticCheck().apply(contractUpdateTxn));

        contractUpdateTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setContractUpdateInstance(
                                ContractUpdateTransactionBody.newBuilder()
                                        .setMemo(memo)
                                        .setContractID(target)
                                        .setStakedNodeId(-3L))
                        .build();
        given(validator.isValidStakedId(any(), any(), anyLong(), any(), any())).willReturn(false);
        assertEquals(INVALID_STAKING_ID, subject.semanticCheck().apply(contractUpdateTxn));
    }

    @Test
    void failsForProxyId() {
        var op =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setContractUpdateInstance(
                                ContractUpdateTransactionBody.newBuilder()
                                        .setMemo(memo)
                                        .setContractID(target)
                                        .setProxyAccountID(proxy));

        contractUpdateTxn = op.build();
        given(accessor.getTxn()).willReturn(contractUpdateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(contracts.get(EntityNum.fromContractId(target))).willReturn(contract);

        assertEquals(
                PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED,
                subject.semanticCheck().apply(contractUpdateTxn));
    }

    private void givenValidTxnCtx() {
        givenValidTxnCtx(true, false, false, false);
    }

    private void givenValidTxnCtxWithStaking() {
        givenValidTxnCtx(true, false, true, true);
    }

    private void givenValidTxnCtx(
            boolean useAutoRenew,
            boolean useMaxAutoAssociations,
            boolean stakingIdSet,
            boolean declineReward) {
        Duration autoRenewDuration =
                Duration.newBuilder().setSeconds(customAutoRenewPeriod).build();
        var op =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setContractUpdateInstance(
                                ContractUpdateTransactionBody.newBuilder()
                                        .setMemo(memo)
                                        .setContractID(target));
        if (useAutoRenew) {
            op.getContractUpdateInstanceBuilder().setAutoRenewPeriod(autoRenewDuration);
            op.getContractUpdateInstanceBuilder()
                    .setMemoWrapper(StringValue.newBuilder().setValue(memo));
        }
        if (useMaxAutoAssociations) {
            op.getContractUpdateInstanceBuilder()
                    .setMaxAutomaticTokenAssociations(Int32Value.of(maxAutoAssociations));
        }
        if (stakingIdSet) {
            op.getContractUpdateInstanceBuilder().setStakedNodeId(10L);
        }
        if (declineReward) {
            op.getContractUpdateInstanceBuilder().setDeclineReward(BoolValue.of(true));
        }
        contractUpdateTxn = op.build();
        given(accessor.getTxn()).willReturn(contractUpdateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(contracts.get(EntityNum.fromContractId(target))).willReturn(contract);
    }

    @Test
    void maxAutoAssociationsSetToZeroIfNotSupported() {
        givenValidTxnCtxWithMaxAssociations();
        customizer = mock(HederaAccountCustomizer.class);
        given(
                        customizerFactory.customizerFor(
                                contract, validator, contractUpdateTxn.getContractUpdateInstance()))
                .willReturn(Pair.of(Optional.of(customizer), OK));
        final Map<AccountProperty, Object> changes = new EnumMap<>(AccountProperty.class);
        changes.put(AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS, 5);
        given(customizer.getChanges()).willReturn(changes);

        subject.doStateTransition();

        assertFalse(changes.containsKey(AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS));
    }

    @Test
    void updateMaxAutomaticAssociationsFailWithMaxLessThanAlreadyExisting() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenValidTxnCtxWithMaxAssociations();
        customizer = mock(HederaAccountCustomizer.class);
        given(
                        customizerFactory.customizerFor(
                                contract, validator, contractUpdateTxn.getContractUpdateInstance()))
                .willReturn(Pair.of(Optional.of(customizer), OK));
        given(customizer.getChanges())
                .willReturn(
                        Map.of(
                                AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS,
                                NEW_MAX_AUTOMATIC_ASSOCIATIONS));
        given(ledger.alreadyUsedAutomaticAssociations(targetId))
                .willReturn(NEW_MAX_AUTOMATIC_ASSOCIATIONS + 1);
        given(dynamicProperties.areContractAutoAssociationsEnabled()).willReturn(true);

        subject.doStateTransition();

        verify(ledger, never()).customize(argThat(target::equals), captor.capture());
        verify(txnCtx).setStatus(EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT);
    }

    @Test
    void updateMaxAutomaticAssociationsFailWithMaxMoreThanAllowedTokenAssociations() {
        final var captor = ArgumentCaptor.forClass(HederaAccountCustomizer.class);
        givenValidTxnCtxWithMaxAssociations();
        given(ledger.alreadyUsedAutomaticAssociations(targetId))
                .willReturn(CUR_MAX_AUTOMATIC_ASSOCIATIONS);
        given(dynamicProperties.areTokenAssociationsLimited()).willReturn(true);
        given(dynamicProperties.maxTokensPerAccount())
                .willReturn(NEW_MAX_AUTOMATIC_ASSOCIATIONS - 1);
        customizer = mock(HederaAccountCustomizer.class);
        given(
                        customizerFactory.customizerFor(
                                contract, validator, contractUpdateTxn.getContractUpdateInstance()))
                .willReturn(Pair.of(Optional.of(customizer), OK));
        given(customizer.getChanges())
                .willReturn(
                        Map.of(
                                AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS,
                                NEW_MAX_AUTOMATIC_ASSOCIATIONS));
        given(dynamicProperties.areContractAutoAssociationsEnabled()).willReturn(true);

        subject.doStateTransition();

        verify(ledger, never()).customize(argThat(target::equals), captor.capture());
        verify(txnCtx).setStatus(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(payer)
                .setTransactionValidStart(
                        Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
                .build();
    }

    private void givenValidTxnCtxWithMaxAssociations() {
        givenValidTxnCtx(true, true, false, false);
    }

    private void withRubberstampingValidator() {
        Duration autoRenewDuration =
                Duration.newBuilder().setSeconds(customAutoRenewPeriod).build();
        given(validator.queryableContractStatus(eq(EntityNum.fromContractId(target)), any()))
                .willReturn(OK);
        given(validator.isValidAutoRenewPeriod(autoRenewDuration)).willReturn(true);
        given(validator.memoCheck(memo)).willReturn(OK);
    }
}
