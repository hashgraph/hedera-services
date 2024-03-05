/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.txns.crypto;

import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static com.hedera.node.app.service.mono.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.node.app.service.mono.ledger.accounts.AliasManager.tryAddressRecovery;
import static com.hedera.node.app.service.mono.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asPrimitiveKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.ledger.BalanceChange;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.records.InProgressChildRecord;
import com.hedera.node.app.service.mono.records.RecordSubmissions;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.FcAssessedCustomFee;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;

public abstract class AbstractAutoCreationLogic {

    private static final List<FcAssessedCustomFee> NO_CUSTOM_FEES = Collections.emptyList();

    protected final Supplier<StateView> currentView;
    protected final UsageLimits usageLimits;
    protected final EntityIdSource ids;
    protected final EntityCreator creator;
    protected final TransactionContext txnCtx;
    protected final SyntheticTxnFactory syntheticTxnFactory;
    protected final List<InProgressChildRecord> pendingCreations = new ArrayList<>();
    protected final Map<ByteString, Set<Id>> tokenAliasMap = new HashMap<>();

    protected final GlobalDynamicProperties properties;
    protected FeeCalculator feeCalculator;

    public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
    public static final String AUTO_MEMO = "auto-created account";
    public static final String LAZY_MEMO = "lazy-created account";

    protected AbstractAutoCreationLogic(
            final UsageLimits usageLimits,
            final SyntheticTxnFactory syntheticTxnFactory,
            final EntityCreator creator,
            final EntityIdSource ids,
            final Supplier<StateView> currentView,
            final TransactionContext txnCtx,
            final GlobalDynamicProperties properties) {
        this.ids = ids;
        this.txnCtx = txnCtx;
        this.creator = creator;
        this.usageLimits = usageLimits;
        this.currentView = currentView;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.properties = properties;
    }

    public void setFeeCalculator(final FeeCalculator feeCalculator) {
        this.feeCalculator = feeCalculator;
    }

    /**
     * Clears any state related to provisionally created accounts and their pending child records.
     */
    public void reset() {
        pendingCreations.clear();
        tokenAliasMap.clear();
    }

    public abstract boolean reclaimPendingAliases();

    public void submitRecords(final RecordSubmissions recordSubmissions) {
        for (final var pendingCreation : pendingCreations) {
            final var syntheticCreation = pendingCreation.syntheticBody();
            final var childRecord = pendingCreation.recordBuilder();
            trackSigImpactIfNeeded(syntheticCreation, childRecord);
            recordSubmissions.submitForTracking(syntheticCreation, childRecord);
        }
    }

    protected abstract void trackSigImpactIfNeeded(
            final Builder syntheticCreation, final ExpirableTxnRecord.Builder childRecord);

    /**
     * Provisionally auto-creates an account in the given accounts ledger for the triggering balance
     * change.
     *
     * <p>Returns the amount deducted from the balance change as an auto-creation charge; or a
     * failure code.
     *
     * <p><b>IMPORTANT:</b> If this change was to be part of a zero-sum balance change list, then
     * after those changes are applied atomically, the returned fee must be given to the funding
     * account!
     *
     * @param change         a triggering change with unique alias
     * @param accountsLedger the accounts ledger to use for the provisional creation
     * @param changes        list of all changes need to construct tokenAliasMap
     * @return the fee charged for the auto-creation if ok, a failure reason otherwise
     */
    public Pair<ResponseCodeEnum, Long> create(
            final BalanceChange change,
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger,
            final List<BalanceChange> changes) {
        if (!usageLimits.areCreatableAccounts(1)) {
            return Pair.of(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED, 0L);
        }

        if (change.isForToken() && !properties.areTokenAutoCreationsEnabled()) {
            return Pair.of(NOT_SUPPORTED, 0L);
        }

        final var alias = change.getNonEmptyAliasIfPresent();
        if (alias == null) {
            throw new IllegalStateException("Cannot auto-create an account from unaliased change " + change);
        }
        TransactionBody.Builder syntheticCreation;
        String memo;
        HederaAccountCustomizer customizer = new HederaAccountCustomizer();
        // checks tokenAliasMap if the change consists an alias that is already used in previous
        // iteration of the token transfer list. This map is used to count number of
        // maxAutoAssociations needed on auto created account
        analyzeTokenTransferCreations(changes);
        final var maxAutoAssociations =
                tokenAliasMap.getOrDefault(alias, Collections.emptySet()).size();
        customizer.maxAutomaticAssociations(maxAutoAssociations);
        final var isAliasEVMAddress = EntityIdUtils.isOfEvmAddressSize(alias);
        if (isAliasEVMAddress) {
            syntheticCreation = syntheticTxnFactory.createHollowAccount(alias, 0L, maxAutoAssociations);
            customizer.key(EMPTY_KEY);
            memo = LAZY_MEMO;
        } else {
            final var key = asPrimitiveKeyUnchecked(alias);
            JKey jKey = asFcKeyUnchecked(key);

            syntheticCreation = syntheticTxnFactory.createAccount(alias, key, 0L, maxAutoAssociations);
            customizer.key(jKey);
            memo = AUTO_MEMO;
        }

        customizer
                .memo(memo)
                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                .expiry(txnCtx.consensusTime().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
                .isReceiverSigRequired(false)
                .isSmartContract(false)
                .alias(alias);
        var fee = 0L;
        final var isSuperUser = txnCtx.activePayer().getAccountNum() == 2L
                || txnCtx.activePayer().getAccountNum() == 50L;
        // If superuser is the payer don't charge fee
        if (!isSuperUser) {
            fee = autoCreationFeeFor(syntheticCreation);
            if (isAliasEVMAddress) {
                fee += getLazyCreationFinalizationFee();
            }
        }
        if (isMirror(ByteStringUtils.unwrapUnsafelyIfPossible(alias))) {
            return Pair.of(INVALID_ALIAS_KEY, fee);
        }

        final var newId = ids.newAccountId();
        accountsLedger.create(newId);
        replaceAliasAndSetBalanceOnChange(change, newId);

        customizer.customize(newId, accountsLedger);

        final var sideEffects = new SideEffectsTracker();
        sideEffects.trackAutoCreation(newId);

        final var childRecord = creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects, memo);

        if (!isAliasEVMAddress) {
            final var key = asPrimitiveKeyUnchecked(alias);

            if (key.hasECDSASecp256K1()) {
                final JKey jKey = asFcKeyUnchecked(key);
                final var evmAddress = tryAddressRecovery(jKey, EthSigsUtils::recoverAddressFromPubKey);
                childRecord.setEvmAddress(evmAddress);
            }
        }

        childRecord.setFee(fee);

        final var inProgress =
                new InProgressChildRecord(DEFAULT_SOURCE_ID, syntheticCreation, childRecord, Collections.emptyList());
        pendingCreations.add(inProgress);

        trackAlias(alias, newId);

        return Pair.of(OK, fee);
    }

    protected abstract void trackAlias(final ByteString alias, final AccountID newId);

    private void replaceAliasAndSetBalanceOnChange(final BalanceChange change, final AccountID newAccountId) {
        if (change.isForHbar()) {
            change.setNewBalance(change.getAggregatedUnits());
        }
        change.replaceNonEmptyAliasWith(EntityNum.fromAccountId(newAccountId));
    }

    private long getLazyCreationFinalizationFee() {
        // an AccountID is already accounted for in the
        // fee estimator, so we just need to pass a stub ECDSA key
        // in the synthetic crypto update body
        final var updateTxnBody =
                CryptoUpdateTransactionBody.newBuilder().setKey(Key.newBuilder().setECDSASecp256K1(ByteString.EMPTY));
        return autoCreationFeeFor(TransactionBody.newBuilder().setCryptoUpdateAccount(updateTxnBody));
    }

    private long autoCreationFeeFor(final TransactionBody.Builder cryptoCreateTxn) {
        final var accessor = MiscUtils.synthAccessorFor(cryptoCreateTxn);
        final var fees = feeCalculator.computeFee(accessor, EMPTY_KEY, currentView.get(), txnCtx.consensusTime());
        return fees.serviceFee() + fees.networkFee() + fees.nodeFee();
    }

    private void analyzeTokenTransferCreations(final List<BalanceChange> changes) {
        for (final var change : changes) {
            if (change.isForHbar()) {
                continue;
            }
            var alias = change.getNonEmptyAliasIfPresent();

            if (alias != null) {
                if (tokenAliasMap.containsKey(alias)) {
                    final var oldSet = tokenAliasMap.get(alias);
                    oldSet.add(change.getToken());
                    tokenAliasMap.put(alias, oldSet);
                } else {
                    tokenAliasMap.put(alias, new HashSet<>(Arrays.asList(change.getToken())));
                }
            }
        }
    }
}
