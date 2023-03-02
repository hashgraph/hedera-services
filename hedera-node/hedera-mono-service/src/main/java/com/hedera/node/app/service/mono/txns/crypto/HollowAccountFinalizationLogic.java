/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.hapi.utils.ByteStringUtils.wrapUnsafely;
import static com.hedera.node.app.service.mono.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.node.app.service.mono.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.records.InProgressChildRecord;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.PendingCompletion;
import com.hedera.node.app.service.mono.utils.accessors.SwirldsTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * <p>Encapsulates the logic that finalizes all hollow accounts based on the list of {@code PendingCompletion} returned
 * by calling {@link SwirldsTxnAccessor#getPendingCompletions} on the current transaction, possibly adding to this list
 * the wrapped hollow sender of an EthereumTransaction.
 *
 * <p>This logic includes:
 * <ul>
 *     <li>checking that the number of requested finalizations is not greater than {@link GlobalDynamicProperties#maxPrecedingRecords()}</li>
 *     <li>updating the state of the accounts with the new key</li>
 *     <li>exporting the preceding child records</li>
 *     <li>marking the entity as changed in the {@link SigImpactHistorian}</li>
 * </ul>
 */
@Singleton
public class HollowAccountFinalizationLogic {

    private final Supplier<AccountStorageAdapter> accountsSupplier;
    private final EntityCreator creator;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final SigImpactHistorian sigImpactHistorian;
    private final RecordsHistorian recordsHistorian;
    private final ExpandHandleSpanMapAccessor spanMapAccessor;
    private final AliasManager aliasManager;
    private final GlobalDynamicProperties properties;
    private final TransactionContext txnCtx;

    @Inject
    public HollowAccountFinalizationLogic(
            final TransactionContext txnCtx,
            final Supplier<AccountStorageAdapter> accountsSupplier,
            final EntityCreator creator,
            final SyntheticTxnFactory syntheticTxnFactory,
            final SigImpactHistorian sigImpactHistorian,
            final RecordsHistorian recordsHistorian,
            final ExpandHandleSpanMapAccessor spanMapAccessor,
            final AliasManager aliasManager,
            final GlobalDynamicProperties properties) {
        this.accountsSupplier = accountsSupplier;
        this.creator = creator;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.sigImpactHistorian = sigImpactHistorian;
        this.recordsHistorian = recordsHistorian;
        this.spanMapAccessor = spanMapAccessor;
        this.aliasManager = aliasManager;
        this.properties = properties;
        this.txnCtx = txnCtx;
    }

    /**
     * For the current transaction, obtains the list of pending completions returned by {@link SwirldsTxnAccessor#getPendingCompletions},
     * and finalizes the accounts based on the data in the contained {@code PendingCompletion}s in the list.
     *
     * <p>If the current transaction is a valid {@link com.hederahashgraph.api.proto.java.HederaFunctionality#EthereumTransaction},
     * and the wrapped sender is also a hollow account, then the sender is also added to the list of pending completions.
     *
     * <p><strong>*IMPORTANT*</strong>
     * The logic here does not verify whether the list of pending completions returned by {@link SwirldsTxnAccessor#getPendingCompletions}
     * is valid --- it assumes they have been verified upstream.
     *
     * @return {@link ResponseCodeEnum#MAX_CHILD_RECORDS_EXCEEDED} if the # of pending completions was >
     * than the max allowed preceding child records; {@link ResponseCodeEnum#OK} otherwise
     */
    public ResponseCodeEnum perform() {
        final var pendingCompletions = getFinalPendingCompletions();
        final int numOfCompletions = pendingCompletions.size();

        if (numOfCompletions > properties.maxPrecedingRecords()) {
            return MAX_CHILD_RECORDS_EXCEEDED;
        } else if (numOfCompletions > 0) {
            finalizeAccounts(pendingCompletions);
        }
        return OK;
    }

    private List<PendingCompletion> getFinalPendingCompletions() {
        var pendingFinalizations = txnCtx.swirldsTxnAccessor().getPendingCompletions();
        if (pendingFinalizations.equals(Collections.emptyList())) {
            pendingFinalizations = new ArrayList<>();
        }
        maybeAddWrappedEthSenderToPendingFinalizations(pendingFinalizations);
        return pendingFinalizations;
    }

    /**
     * Given a mutable list of {@link PendingCompletion}, checks whether the current transaction
     * is a valid EthereumTransaction and its sender sender is a hollow account. If such is the case,
     * adds a new {@link PendingCompletion} for the hollow wrapped sender of the EthereumTransaction.
     *
     * @param pendingFinalizations a mutable list of {@link PendingCompletion}
     */
    private void maybeAddWrappedEthSenderToPendingFinalizations(final List<PendingCompletion> pendingFinalizations) {
        final var accessor = txnCtx.accessor();
        final var ethTxExpansion = spanMapAccessor.getEthTxExpansion(accessor);
        if (ethTxExpansion != null && ethTxExpansion.result().equals(OK)) {
            final var ethTxSigs = spanMapAccessor.getEthTxSigsMeta(accessor);
            final var address = ethTxSigs.address();
            final var accountNum = aliasManager.lookupIdBy(wrapUnsafely(address));
            if (accountNum != EntityNum.MISSING_NUM) {
                final var hederaAccount = accountsSupplier.get().get(accountNum);
                if (EMPTY_KEY.equals(hederaAccount.getAccountKey())) {
                    final var key = new JECDSASecp256k1Key(ethTxSigs.publicKey());
                    pendingFinalizations.add(new PendingCompletion(accountNum, key));
                }
            }
        }
    }

    /***
     * Finalizes the {@link com.hedera.node.app.service.mono.state.migration.HederaAccount}s
     * in {@param pendingCompletions} in state, exports child records and updates {@link SigImpactHistorian}.
     *
     * <p>NOTE that even if the subsequent transaction execution is not successful,
     *         the hollow accounts will still be finalized.
     * @param pendingCompletions the list of {@link PendingCompletion} that specify the hollow account finalizations
     * to be performed
     */
    private void finalizeAccounts(final List<PendingCompletion> pendingCompletions) {
        final var accountStorageAdapter = accountsSupplier.get();
        for (final var completion : pendingCompletions) {
            final var hederaAccount = accountStorageAdapter.getForModify(completion.hollowAccountNum());
            final var key = completion.key();
            hederaAccount.setAccountKey(key);
            trackHollowAccountCompletion(hederaAccount.getEntityNum().toGrpcAccountId(), key);
        }
    }

    private void trackHollowAccountCompletion(final AccountID accountID, final JKey key) {
        final var accountKey = Key.newBuilder()
                .setECDSASecp256K1(ByteString.copyFrom(key.getECDSASecp256k1Key()))
                .build();
        final var syntheticUpdate =
                syntheticTxnFactory.updateHollowAccount(EntityNum.fromAccountId(accountID), accountKey);
        final var sideEffects = new SideEffectsTracker();
        sideEffects.trackHollowAccountUpdate(accountID);
        final var childRecordBuilder =
                creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, null);
        final var inProgress = new InProgressChildRecord(
                DEFAULT_SOURCE_ID, syntheticUpdate, childRecordBuilder, Collections.emptyList());
        final var childRecord = inProgress.recordBuilder();
        recordsHistorian.trackPrecedingChildRecord(DEFAULT_SOURCE_ID, inProgress.syntheticBody(), childRecord);

        sigImpactHistorian.markEntityChanged(accountID.getAccountNum());
    }
}
