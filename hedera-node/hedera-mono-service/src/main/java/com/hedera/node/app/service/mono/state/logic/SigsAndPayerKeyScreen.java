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
package com.hedera.node.app.service.mono.state.logic;

import static com.hedera.node.app.hapi.utils.ByteStringUtils.wrapUnsafely;
import static com.hedera.node.app.service.mono.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.node.app.service.mono.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
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
import com.hedera.node.app.service.mono.sigs.Rationalization;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.stats.MiscSpeedometers;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.SwirldsTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.Collections;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class SigsAndPayerKeyScreen {
    private static final Logger log = LogManager.getLogger(SigsAndPayerKeyScreen.class);

    private final Rationalization rationalization;
    private final PayerSigValidity payerSigValidity;
    private final MiscSpeedometers speedometers;
    private final TransactionContext txnCtx;
    private final BiPredicate<JKey, TransactionSignature> validityTest;
    private final Supplier<AccountStorageAdapter> accounts;
    private EntityCreator creator;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final SigImpactHistorian sigImpactHistorian;
    private final RecordsHistorian recordsHistorian;
    private final ExpandHandleSpanMapAccessor spanMapAccessor;
    private final AliasManager aliasManager;
    private final GlobalDynamicProperties properties;

    @Inject
    public SigsAndPayerKeyScreen(
            final Rationalization rationalization,
            final PayerSigValidity payerSigValidity,
            final TransactionContext txnCtx,
            final MiscSpeedometers speedometers,
            final BiPredicate<JKey, TransactionSignature> validityTest,
            final Supplier<AccountStorageAdapter> accounts,
            final EntityCreator creator,
            final SyntheticTxnFactory syntheticTxnFactory,
            final SigImpactHistorian sigImpactHistorian,
            final RecordsHistorian recordsHistorian,
            final ExpandHandleSpanMapAccessor spanMapAccessor,
            final AliasManager aliasManager,
            final GlobalDynamicProperties properties) {
        this.txnCtx = txnCtx;
        this.validityTest = validityTest;
        this.speedometers = speedometers;
        this.rationalization = rationalization;
        this.payerSigValidity = payerSigValidity;
        this.accounts = accounts;
        this.creator = creator;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.sigImpactHistorian = sigImpactHistorian;
        this.recordsHistorian = recordsHistorian;
        this.spanMapAccessor = spanMapAccessor;
        this.aliasManager = aliasManager;
        this.properties = properties;
    }

    public ResponseCodeEnum applyTo(SwirldsTxnAccessor accessor) {
        rationalization.performFor(accessor);

        final var sigStatus = rationalization.finalStatus();
        if (sigStatus == OK && rationalization.usedSyncVerification()) {
            speedometers.cycleSyncVerifications();
        }

        final var sigMeta = accessor.getSigMeta();
        sigMeta.replacePayerHollowKeyIfNeeded(accessor.getSigMap());

        if (hasActivePayerSig(accessor)) {
            txnCtx.payerSigIsKnownActive();

            if (sigMeta.hasReplacedHollowKey()) {
                accounts.get()
                        .getForModify(EntityNum.fromAccountId(txnCtx.activePayer()))
                        .setAccountKey(sigMeta.payerKey());
                trackHollowAccountCompletion(txnCtx.activePayer(), sigMeta.payerKey());
            }
        }

        final var ethTxExpansion = spanMapAccessor.getEthTxExpansion(accessor);
        if (properties.isLazyCreationEnabled()
                && ethTxExpansion != null
                && ethTxExpansion.result().equals(OK)) {
            final var ethTxSigs = spanMapAccessor.getEthTxSigsMeta(accessor);
            final var callerNum = aliasManager.lookupIdBy(wrapUnsafely(ethTxSigs.address()));
            if (callerNum != EntityNum.MISSING_NUM) {
                final var account = accounts.get().get(callerNum);
                if (EMPTY_KEY.equals(account.getAccountKey())) {
                    var key = new JECDSASecp256k1Key(ethTxSigs.publicKey());
                    var accountToModify = accounts.get().getForModify(callerNum);
                    accountToModify.setAccountKey(key);
                    trackHollowAccountCompletion(callerNum.toGrpcAccountId(), key);
                }
            }
        }

        return sigStatus;
    }

    private void trackHollowAccountCompletion(AccountID accountID, JKey key) {
        final var accountKey =
                Key.newBuilder()
                        .setECDSASecp256K1(ByteString.copyFrom(key.getECDSASecp256k1Key()))
                        .build();

        var syntheticUpdate =
                syntheticTxnFactory.updateHollowAccount(
                        EntityNum.fromAccountId(accountID), accountKey);

        final var sideEffects = new SideEffectsTracker();
        sideEffects.trackHollowAccountUpdate(accountID);

        final var childRecordBuilder =
                creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, null);

        final var inProgress =
                new InProgressChildRecord(
                        DEFAULT_SOURCE_ID,
                        syntheticUpdate,
                        childRecordBuilder,
                        Collections.emptyList());

        final var childRecord = inProgress.recordBuilder();
        sigImpactHistorian.markEntityChanged(childRecord.getReceiptBuilder().getAccountId().num());
        recordsHistorian.trackPrecedingChildRecord(
                DEFAULT_SOURCE_ID, inProgress.syntheticBody(), childRecord);
    }

    private boolean hasActivePayerSig(SwirldsTxnAccessor accessor) {
        try {
            return payerSigValidity.test(accessor, validityTest);
        } catch (Exception unknown) {
            log.warn("Unhandled exception while testing payer sig activation", unknown);
        }
        return false;
    }
}
