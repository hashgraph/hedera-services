/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.ledger.ids.ExceptionalEntityIdSource.NOOP_ID_SOURCE;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.charging.FeeDistribution;
import com.hedera.node.app.service.mono.grpc.marshalling.AliasResolver;
import com.hedera.node.app.service.mono.grpc.marshalling.BalanceChangeManager;
import com.hedera.node.app.service.mono.grpc.marshalling.CustomSchedulesManager;
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
import com.hedera.node.app.service.mono.records.RecordSubmissions;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.txns.crypto.AbstractAutoCreationLogic;
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
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
public class InfrastructureFactory {
    private final UsageLimits usageLimits;
    private final EntityIdSource ids;
    private final OptionValidator validator;
    private final RecordsHistorian recordsHistorian;
    private final SigImpactHistorian sigImpactHistorian;
    private final DissociationFactory dissociationFactory;
    private final GlobalDynamicProperties dynamicProperties;
    private final TransactionContext txnCtx;
    private final AliasManager aliasManager;
    private final FeeDistribution feeDistribution;
    private final FeeAssessor feeAssessor;
    private final Supplier<AliasResolver> aliasResolverFactory;
    private final PureTransferSemanticChecks checks;
    private final BalanceChangeManager.ChangeManagerFactory changeManagerFactory;
    private final Predicate<CryptoTransferTransactionBody> aliasCheck;
    private final Function<CustomFeeSchedules, CustomSchedulesManager> schedulesManagerFactory;
    private final Provider<FeeCalculator> feeCalculator;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final StateView view;
    private final EntityCreator entityCreator;

    @Inject
    public InfrastructureFactory(
            final UsageLimits usageLimits,
            final EntityIdSource ids,
            final EvmEncodingFacade evmEncoder,
            final OptionValidator validator,
            final RecordsHistorian recordsHistorian,
            final SigImpactHistorian sigImpactHistorian,
            final DissociationFactory dissociationFactory,
            final GlobalDynamicProperties dynamicProperties,
            final TransactionContext txnCtx,
            final AliasManager aliasManager,
            final FeeDistribution feeDistribution,
            final FeeAssessor feeAssessor,
            final PureTransferSemanticChecks checks,
            final Provider<FeeCalculator> feeCalculator,
            final SyntheticTxnFactory syntheticTxnFactory,
            final StateView view,
            final EntityCreator entityCreator) {
        this.ids = ids;
        this.validator = validator;
        this.usageLimits = usageLimits;
        this.recordsHistorian = recordsHistorian;
        this.dynamicProperties = dynamicProperties;
        this.sigImpactHistorian = sigImpactHistorian;
        this.dissociationFactory = dissociationFactory;
        this.txnCtx = txnCtx;
        this.aliasManager = aliasManager;
        this.feeDistribution = feeDistribution;
        this.feeAssessor = feeAssessor;
        this.aliasResolverFactory = AliasResolver::new;
        this.checks = checks;
        this.changeManagerFactory = BalanceChangeManager::new;
        this.aliasCheck = AliasResolver::usesAliases;
        this.schedulesManagerFactory = CustomSchedulesManager::new;
        this.feeCalculator = feeCalculator;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.view = view;
        this.entityCreator = entityCreator;
    }

    public SideEffectsTracker newSideEffects() {
        return new SideEffectsTracker();
    }

    public AccountStore newAccountStore(final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts) {
        return new AccountStore(validator, accounts);
    }

    public TypedTokenStore newTokenStore(
            final AccountStore accountStore,
            final SideEffectsTracker sideEffects,
            final BackingStore<TokenID, MerkleToken> tokens,
            final BackingStore<NftId, UniqueTokenAdapter> uniqueTokens,
            final BackingStore<Pair<AccountID, TokenID>, HederaTokenRel> tokenRels) {
        return new TypedTokenStore(accountStore, tokens, uniqueTokens, tokenRels, sideEffects);
    }

    public HederaTokenStore newHederaTokenStore(
            final SideEffectsTracker sideEffects,
            final BackingStore<TokenID, MerkleToken> backingTokens,
            final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger,
            final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel> tokenRelsLedger) {
        return new HederaTokenStore(
                NOOP_ID_SOURCE,
                usageLimits,
                validator,
                sideEffects,
                dynamicProperties,
                tokenRelsLedger,
                nftsLedger,
                backingTokens);
    }

    public ImpliedTransfersMarshal newImpliedTransfersMarshal(final CustomFeeSchedules customFeeSchedules) {
        return new ImpliedTransfersMarshal(
                feeAssessor,
                aliasManager,
                customFeeSchedules,
                aliasResolverFactory,
                dynamicProperties,
                checks,
                aliasCheck,
                changeManagerFactory,
                schedulesManagerFactory);
    }

    public BurnLogic newBurnLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
        return new BurnLogic(validator, tokenStore, accountStore, dynamicProperties);
    }

    public DeleteLogic newDeleteLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
        return new DeleteLogic(accountStore, tokenStore, sigImpactHistorian);
    }

    public MintLogic newMintLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
        return new MintLogic(usageLimits, validator, tokenStore, accountStore, dynamicProperties);
    }

    public AssociateLogic newAssociateLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
        return new AssociateLogic(usageLimits, tokenStore, accountStore, dynamicProperties);
    }

    public DissociateLogic newDissociateLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
        return new DissociateLogic(validator, tokenStore, accountStore, dissociationFactory);
    }

    public CreateLogic newTokenCreateLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
        return new CreateLogic(
                usageLimits, accountStore, tokenStore, dynamicProperties, sigImpactHistorian, ids, validator);
    }

    public TransferLogic newTransferLogic(
            final HederaTokenStore tokenStore,
            final SideEffectsTracker sideEffects,
            final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger,
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger,
            final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel> tokenRelsLedger) {
        return new TransferLogic(
                accountsLedger,
                nftsLedger,
                tokenRelsLedger,
                tokenStore,
                sideEffects,
                validator,
                null,
                recordsHistorian,
                txnCtx,
                aliasManager,
                feeDistribution
                // No-op, we won't have used any frontend throttle capacity for an auto-creation
                // attempted during an EVM transaction
                );
    }

    public ApproveAllowanceLogic newApproveAllowanceLogic(
            final AccountStore accountStore, final TypedTokenStore tokenStore) {
        return new ApproveAllowanceLogic(accountStore, tokenStore, dynamicProperties);
    }

    public DeleteAllowanceLogic newDeleteAllowanceLogic(
            final AccountStore accountStore, final TypedTokenStore tokenStore) {
        return new DeleteAllowanceLogic(accountStore, tokenStore);
    }

    public GrantKycLogic newGrantKycLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
        return new GrantKycLogic(tokenStore, accountStore);
    }

    public RevokeKycLogic newRevokeKycLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
        return new RevokeKycLogic(tokenStore, accountStore);
    }

    public PauseLogic newPauseLogic(final TypedTokenStore tokenStore) {
        return new PauseLogic(tokenStore);
    }

    public UnpauseLogic newUnpauseLogic(final TypedTokenStore tokenStore) {
        return new UnpauseLogic(tokenStore);
    }

    public WipeLogic newWipeLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
        return new WipeLogic(tokenStore, accountStore, dynamicProperties);
    }

    public FreezeLogic newFreezeLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
        return new FreezeLogic(tokenStore, accountStore);
    }

    public UnfreezeLogic newUnfreezeLogic(final AccountStore accountStore, final TypedTokenStore tokenStore) {
        return new UnfreezeLogic(tokenStore, accountStore);
    }

    public CreateChecks newCreateChecks() {
        return new CreateChecks(dynamicProperties, validator);
    }

    public ApproveAllowanceChecks newApproveAllowanceChecks() {
        return new ApproveAllowanceChecks(dynamicProperties, validator);
    }

    public DeleteAllowanceChecks newDeleteAllowanceChecks() {
        return new DeleteAllowanceChecks(dynamicProperties, validator);
    }

    public TokenUpdateLogic newTokenUpdateLogic(
            HederaTokenStore hederaTokenStore, WorldLedgers ledgers, SideEffectsTracker sideEffects) {
        return new TokenUpdateLogic(
                dynamicProperties.treasuryNftAllowance(),
                validator,
                hederaTokenStore,
                ledgers,
                sideEffects,
                sigImpactHistorian);
    }

    public AbstractAutoCreationLogic newAutoCreationLogicScopedTo(final HederaStackedWorldStateUpdater updater) {
        final var autoCreationLogic = new EvmAutoCreationLogic(
                usageLimits,
                syntheticTxnFactory,
                entityCreator,
                ids,
                () -> view,
                txnCtx,
                dynamicProperties,
                updater.aliases());
        autoCreationLogic.setFeeCalculator(feeCalculator.get());
        return autoCreationLogic;
    }

    public RecordSubmissions newRecordSubmissionsScopedTo(final HederaStackedWorldStateUpdater updater) {
        return (txnBody, txnRecord) -> {
            txnRecord.onlyExternalizeIfSuccessful();
            updater.manageInProgressPrecedingRecord(recordsHistorian, txnRecord, txnBody);
        };
    }
}
