/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.crypto;

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.InProgressChildRecord;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Responsible for creating accounts during a crypto transfer that sends hbar to a previously unused
 * alias.
 */
@Singleton
public class AutoCreationLogic {
    private static final List<FcAssessedCustomFee> NO_CUSTOM_FEES = Collections.emptyList();

    private final StateView currentView;
    private final EntityIdSource ids;
    private final EntityCreator creator;
    private final TransactionContext txnCtx;
    private final AliasManager aliasManager;
    private final SigImpactHistorian sigImpactHistorian;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final List<InProgressChildRecord> pendingCreations = new ArrayList<>();

    private FeeCalculator feeCalculator;

    public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
    public static final String AUTO_MEMO = "auto-created account";

    @Inject
    public AutoCreationLogic(
            final SyntheticTxnFactory syntheticTxnFactory,
            final EntityCreator creator,
            final EntityIdSource ids,
            final AliasManager aliasManager,
            final SigImpactHistorian sigImpactHistorian,
            final StateView currentView,
            final TransactionContext txnCtx) {
        this.ids = ids;
        this.txnCtx = txnCtx;
        this.creator = creator;
        this.currentView = currentView;
        this.sigImpactHistorian = sigImpactHistorian;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.aliasManager = aliasManager;
    }

    public void setFeeCalculator(final FeeCalculator feeCalculator) {
        this.feeCalculator = feeCalculator;
    }

    /**
     * Clears any state related to provisionally created accounts and their pending child records.
     */
    public void reset() {
        pendingCreations.clear();
    }

    /**
     * Removes any aliases added to the {@link AliasManager} map as part of provisional creations.
     *
     * @return whether any aliases were removed
     */
    public boolean reclaimPendingAliases() {
        if (!pendingCreations.isEmpty()) {
            for (final var pendingCreation : pendingCreations) {
                final var alias = pendingCreation.recordBuilder().getAlias();
                aliasManager.unlink(alias);
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Notifies the given {@link RecordsHistorian} of the child records for any provisionally
     * created accounts since the last call to {@link AutoCreationLogic#reset()}.
     *
     * @param recordsHistorian the records historian that should track the child records
     */
    public void submitRecordsTo(final RecordsHistorian recordsHistorian) {
        for (final var pendingCreation : pendingCreations) {
            final var syntheticCreation = pendingCreation.syntheticBody();
            final var childRecord = pendingCreation.recordBuilder();
            sigImpactHistorian.markAliasChanged(childRecord.getAlias());
            sigImpactHistorian.markEntityChanged(
                    childRecord.getReceiptBuilder().getAccountId().num());
            recordsHistorian.trackPrecedingChildRecord(
                    DEFAULT_SOURCE_ID, syntheticCreation, childRecord);
        }
    }

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
     * @param change a triggering change with unique alias
     * @param accountsLedger the accounts ledger to use for the provisional creation
     * @return the fee charged for the auto-creation if ok, a failure reason otherwise
     */
    public Pair<ResponseCodeEnum, Long> create(
            final BalanceChange change,
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
        final var alias = change.alias();
        final var key = asPrimitiveKeyUnchecked(alias);
        final var syntheticCreation = syntheticTxnFactory.createAccount(key, 0L);
        final var fee = autoCreationFeeFor(syntheticCreation);
        if (fee > change.getAggregatedUnits()) {
            return Pair.of(change.codeForInsufficientBalance(), 0L);
        }
        change.aggregateUnits(-fee);
        change.setNewBalance(change.getAggregatedUnits());

        final var sideEffects = new SideEffectsTracker();
        // tbd - this is not correct, syntheticCreation.getTransactionID().getAccountID() is always
        // the default account id instance
        // and it only works because the fields we extract from the account id here should always be
        // 0
        final var newId = ids.newAccountId(syntheticCreation.getTransactionID().getAccountID());
        accountsLedger.create(newId);
        change.replaceAliasWith(newId);
        JKey jKey = asFcKeyUnchecked(key);
        final var customizer =
                new HederaAccountCustomizer()
                        .key(jKey)
                        .memo(AUTO_MEMO)
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                        .expiry(txnCtx.consensusTime().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
                        .isReceiverSigRequired(false)
                        .isSmartContract(false)
                        .alias(alias);
        customizer.customize(newId, accountsLedger);
        sideEffects.trackAutoCreation(newId, alias);
        final var childRecord =
                creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffects, AUTO_MEMO);
        childRecord.setFee(fee);
        final var inProgress =
                new InProgressChildRecord(
                        DEFAULT_SOURCE_ID, syntheticCreation, childRecord, Collections.emptyList());
        pendingCreations.add(inProgress);
        // If the transaction fails, we will get an opportunity to unlink this alias in
        // reclaimPendingAliases()
        aliasManager.link(alias, EntityNum.fromAccountId(newId));
        aliasManager.maybeLinkEvmAddress(jKey, EntityNum.fromAccountId(newId));

        return Pair.of(OK, fee);
    }

    private long autoCreationFeeFor(final TransactionBody.Builder cryptoCreateTxn) {
        final var signedTxn =
                SignedTransaction.newBuilder()
                        .setBodyBytes(cryptoCreateTxn.build().toByteString())
                        .setSigMap(SignatureMap.getDefaultInstance())
                        .build();
        final var txn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(signedTxn.toByteString())
                        .build();

        final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
        final var fees =
                feeCalculator.computeFee(accessor, EMPTY_KEY, currentView, txnCtx.consensusTime());
        return fees.getServiceFee() + fees.getNetworkFee() + fees.getNodeFee();
    }

    private Key asPrimitiveKeyUnchecked(final ByteString alias) {
        try {
            return Key.parseFrom(alias);
        } catch (InvalidProtocolBufferException internal) {
            throw new IllegalStateException(internal);
        }
    }
}
