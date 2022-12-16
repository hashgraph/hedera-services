/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts.precompile;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.charging.FeeDistribution;
import com.hedera.node.app.service.mono.grpc.marshalling.FeeAssessor;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.node.app.service.mono.ledger.PureTransferSemanticChecks;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.TransferLogic;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.proxy.RedirectViewExecutor;
import com.hedera.node.app.service.mono.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.txns.crypto.ApproveAllowanceLogic;
import com.hedera.node.app.service.mono.txns.crypto.DeleteAllowanceLogic;
import com.hedera.node.app.service.mono.txns.crypto.EvmAutoCreationLogic;
import com.hedera.node.app.service.mono.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.node.app.service.mono.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.node.app.service.mono.txns.customfees.CustomFeeSchedules;
import com.hedera.node.app.service.mono.txns.token.AssociateLogic;
import com.hedera.node.app.service.mono.txns.token.BurnLogic;
import com.hedera.node.app.service.mono.txns.token.CreateLogic;
import com.hedera.node.app.service.mono.txns.token.DeleteLogic;
import com.hedera.node.app.service.mono.txns.token.DissociateLogic;
import com.hedera.node.app.service.mono.txns.token.FreezeLogic;
import com.hedera.node.app.service.mono.txns.token.GrantKycLogic;
import com.hedera.node.app.service.mono.txns.token.MintLogic;
import com.hedera.node.app.service.mono.txns.token.PauseLogic;
import com.hedera.node.app.service.mono.txns.token.RevokeKycLogic;
import com.hedera.node.app.service.mono.txns.token.UnfreezeLogic;
import com.hedera.node.app.service.mono.txns.token.UnpauseLogic;
import com.hedera.node.app.service.mono.txns.token.WipeLogic;
import com.hedera.node.app.service.mono.txns.token.process.DissociationFactory;
import com.hedera.node.app.service.mono.txns.token.validators.CreateChecks;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import javax.inject.Provider;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InfrastructureFactoryTest {
    @Mock private UsageLimits usageLimits;
    @Mock private EntityIdSource ids;
    @Mock private EncodingFacade encoder;
    @Mock private EvmEncodingFacade evmEncoder;
    @Mock private OptionValidator validator;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private DissociationFactory dissociationFactory;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
            tokenRelsLedger;

    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;
    @Mock private BackingStore<TokenID, MerkleToken> tokens;
    @Mock private BackingStore<NftId, UniqueTokenAdapter> uniqueTokens;
    @Mock private BackingStore<Pair<AccountID, TokenID>, HederaTokenRel> tokenRels;
    @Mock private MessageFrame frame;
    @Mock private ViewGasCalculator gasCalculator;
    @Mock private HederaStackedWorldStateUpdater worldStateUpdater;
    @Mock private WorldLedgers ledgers;
    @Mock private TransactionContext txnCtx;
    @Mock private AliasManager aliasManager;
    @Mock private FeeDistribution feeDistribution;
    @Mock private FeeAssessor feeAssessor;
    @Mock private PureTransferSemanticChecks checks;
    @Mock private CustomFeeSchedules customFeeSchedules;
    @Mock private Provider<FeeCalculator> feeCalculator;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private StateView view;
    @Mock private EntityCreator entityCreator;

    private InfrastructureFactory subject;

    @BeforeEach
    void setUp() {
        subject =
                new InfrastructureFactory(
                        usageLimits,
                        ids,
                        encoder,
                        evmEncoder,
                        validator,
                        recordsHistorian,
                        sigImpactHistorian,
                        dissociationFactory,
                        dynamicProperties,
                        txnCtx,
                        aliasManager,
                        feeDistribution,
                        feeAssessor,
                        checks,
                        feeCalculator,
                        syntheticTxnFactory,
                        view,
                        entityCreator);
    }

    @Test
    void canCreateSideEffects() {
        assertInstanceOf(SideEffectsTracker.class, subject.newSideEffects());
    }

    @Test
    void canCreateImpliedTransfersMarshal() {
        assertInstanceOf(
                ImpliedTransfersMarshal.class,
                subject.newImpliedTransfersMarshal(customFeeSchedules));
    }

    @Test
    void canCreateAccountStore() {
        assertInstanceOf(AccountStore.class, subject.newAccountStore(accounts));
    }

    @Test
    void canCreateNewTokenStore() {
        assertInstanceOf(
                TypedTokenStore.class,
                subject.newTokenStore(
                        subject.newAccountStore(accounts),
                        subject.newSideEffects(),
                        tokens,
                        uniqueTokens,
                        tokenRels));
    }

    @Test
    void canCreateNewAutoCreationLogc() {
        assertInstanceOf(
                EvmAutoCreationLogic.class,
                subject.newAutoCreationLogicScopedTo(mock(HederaStackedWorldStateUpdater.class)));
    }

    @Test
    void canCreateNewHederaTokenStore() {
        assertInstanceOf(
                HederaTokenStore.class,
                subject.newHederaTokenStore(
                        subject.newSideEffects(), tokens, nftsLedger, tokenRelsLedger));
    }

    @Test
    void canCreateNewBurnLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        assertInstanceOf(
                BurnLogic.class,
                subject.newBurnLogic(
                        accountStore,
                        subject.newTokenStore(
                                accountStore,
                                subject.newSideEffects(),
                                tokens,
                                uniqueTokens,
                                tokenRels)));
    }

    @Test
    void canCreateNewDeleteLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        final var tokenStore =
                subject.newTokenStore(
                        accountStore, subject.newSideEffects(), tokens, uniqueTokens, tokenRels);
        assertInstanceOf(DeleteLogic.class, subject.newDeleteLogic(accountStore, tokenStore));
    }

    @Test
    void canCreateNewMintLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        assertInstanceOf(
                MintLogic.class,
                subject.newMintLogic(
                        accountStore,
                        subject.newTokenStore(
                                accountStore,
                                subject.newSideEffects(),
                                tokens,
                                uniqueTokens,
                                tokenRels)));
    }

    @Test
    void canCreateNewAssociateLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        assertInstanceOf(
                AssociateLogic.class,
                subject.newAssociateLogic(
                        accountStore,
                        subject.newTokenStore(
                                accountStore,
                                subject.newSideEffects(),
                                tokens,
                                uniqueTokens,
                                tokenRels)));
    }

    @Test
    void canCreateNewDissociateLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        assertInstanceOf(
                DissociateLogic.class,
                subject.newDissociateLogic(
                        accountStore,
                        subject.newTokenStore(
                                accountStore,
                                subject.newSideEffects(),
                                tokens,
                                uniqueTokens,
                                tokenRels)));
    }

    @Test
    void canCreateNewTokenCreateLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        assertInstanceOf(
                CreateLogic.class,
                subject.newTokenCreateLogic(
                        accountStore,
                        subject.newTokenStore(
                                accountStore,
                                subject.newSideEffects(),
                                tokens,
                                uniqueTokens,
                                tokenRels)));
    }

    @Test
    void canCreateNewTransferLogic() {
        final var sideEffects = subject.newSideEffects();
        assertInstanceOf(
                TransferLogic.class,
                subject.newTransferLogic(
                        subject.newHederaTokenStore(
                                sideEffects, tokens, nftsLedger, tokenRelsLedger),
                        sideEffects,
                        nftsLedger,
                        accounts,
                        tokenRelsLedger));
    }

    @Test
    void canCreateNewRedirectExecutor() {
        given(frame.getWorldUpdater()).willReturn(worldStateUpdater);

        assertInstanceOf(
                RedirectViewExecutor.class,
                subject.newRedirectExecutor(Bytes.EMPTY, frame, gasCalculator));
    }

    @Test
    void canCreateNewApproveAllowanceLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        final var tokenStore =
                subject.newTokenStore(
                        accountStore, subject.newSideEffects(), tokens, uniqueTokens, tokenRels);
        assertInstanceOf(
                ApproveAllowanceLogic.class,
                subject.newApproveAllowanceLogic(accountStore, tokenStore));
    }

    @Test
    void canCreateNewDeleteAllowanceLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        final var tokenStore =
                subject.newTokenStore(
                        accountStore, subject.newSideEffects(), tokens, uniqueTokens, tokenRels);
        assertInstanceOf(
                DeleteAllowanceLogic.class,
                subject.newDeleteAllowanceLogic(accountStore, tokenStore));
    }

    @Test
    void canCreateNewCreateChecks() {
        assertInstanceOf(CreateChecks.class, subject.newCreateChecks());
    }

    @Test
    void canCreateNewApproveAllowanceChecks() {
        assertInstanceOf(ApproveAllowanceChecks.class, subject.newApproveAllowanceChecks());
    }

    @Test
    void canCreateNewDeleteAllowanceChecks() {
        assertInstanceOf(DeleteAllowanceChecks.class, subject.newDeleteAllowanceChecks());
    }

    @Test
    void canCreateNewGrantKycLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        final var tokenStore =
                subject.newTokenStore(
                        accountStore, subject.newSideEffects(), tokens, uniqueTokens, tokenRels);
        assertInstanceOf(GrantKycLogic.class, subject.newGrantKycLogic(accountStore, tokenStore));
    }

    @Test
    void canCreateNewRevokeKycLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        final var tokenStore =
                subject.newTokenStore(
                        accountStore, subject.newSideEffects(), tokens, uniqueTokens, tokenRels);
        assertInstanceOf(RevokeKycLogic.class, subject.newRevokeKycLogic(accountStore, tokenStore));
    }

    @Test
    void canCreateNewPauseLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        final var tokenStore =
                subject.newTokenStore(
                        accountStore, subject.newSideEffects(), tokens, uniqueTokens, tokenRels);
        assertInstanceOf(PauseLogic.class, subject.newPauseLogic(tokenStore));
    }

    @Test
    void canCreateNewUnpauseLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        final var tokenStore =
                subject.newTokenStore(
                        accountStore, subject.newSideEffects(), tokens, uniqueTokens, tokenRels);
        assertInstanceOf(UnpauseLogic.class, subject.newUnpauseLogic(tokenStore));
    }

    @Test
    void canCreateNewWipeLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        final var tokenStore =
                subject.newTokenStore(
                        accountStore, subject.newSideEffects(), tokens, uniqueTokens, tokenRels);
        assertInstanceOf(WipeLogic.class, subject.newWipeLogic(accountStore, tokenStore));
    }

    @Test
    void canCreateNewFreezeLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        final var tokenStore =
                subject.newTokenStore(
                        accountStore, subject.newSideEffects(), tokens, uniqueTokens, tokenRels);
        assertInstanceOf(FreezeLogic.class, subject.newFreezeLogic(accountStore, tokenStore));
    }

    @Test
    void canCreateNewUnfreezeLogic() {
        final var accountStore = subject.newAccountStore(accounts);
        final var tokenStore =
                subject.newTokenStore(
                        accountStore, subject.newSideEffects(), tokens, uniqueTokens, tokenRels);
        assertInstanceOf(UnfreezeLogic.class, subject.newUnfreezeLogic(accountStore, tokenStore));
    }

    @Test
    void canCreateNewUpdateLogic() {
        final var sideEffects = subject.newSideEffects();
        assertInstanceOf(
                TokenUpdateLogic.class,
                subject.newTokenUpdateLogic(
                        subject.newHederaTokenStore(
                                sideEffects, tokens, nftsLedger, tokenRelsLedger),
                        ledgers,
                        sideEffects));
    }

    @Test
    void canCreateNewRecordSubmissions() {
        final var updater = mock(HederaStackedWorldStateUpdater.class);
        final var recordSubmissions = subject.newRecordSubmissionsScopedTo(updater);
        final var expirableTxnRecord = mock(ExpirableTxnRecord.Builder.class);
        final var txnBodyBuilder = mock(Builder.class);

        recordSubmissions.submitForTracking(txnBodyBuilder, expirableTxnRecord);

        verify(expirableTxnRecord).onlyExternalizeIfSuccessful();
        verify(updater)
                .manageInProgressPrecedingRecord(
                        recordsHistorian, expirableTxnRecord, txnBodyBuilder);
    }
}
