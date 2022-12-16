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
package com.hedera.node.app.service.mono.txns.crypto;

import static com.hedera.node.app.service.mono.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asPrimitiveKeyUnchecked;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.SigImpactHistorian;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.records.InProgressChildRecord;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Responsible for creating accounts during a crypto transfer that sends hbar to a previously unused
 * alias.
 */
@Singleton
public class AutoCreationLogic extends AbstractAutoCreationLogic {
    private final AliasManager aliasManager;
    private final SigImpactHistorian sigImpactHistorian;

    @Inject
    public AutoCreationLogic(
            final UsageLimits usageLimits,
            final SyntheticTxnFactory syntheticTxnFactory,
            final EntityCreator creator,
            final EntityIdSource ids,
            final AliasManager aliasManager,
            final SigImpactHistorian sigImpactHistorian,
            final Supplier<StateView> currentView,
            final TransactionContext txnCtx,
            final GlobalDynamicProperties properties) {
        super(usageLimits, syntheticTxnFactory, creator, ids, currentView, txnCtx, properties);
        this.sigImpactHistorian = sigImpactHistorian;
        this.aliasManager = aliasManager;
    }

    @Override
    protected void trackAlias(final ByteString alias, final AccountID newId) {
        // If the transaction fails, we will get an opportunity to unlink this alias in
        // reclaimPendingAliases()
        aliasManager.link(alias, EntityNum.fromAccountId(newId));
        if (alias.size() > EntityIdUtils.EVM_ADDRESS_SIZE) {
            final var key = asPrimitiveKeyUnchecked(alias);
            JKey jKey = asFcKeyUnchecked(key);
            aliasManager.maybeLinkEvmAddress(jKey, EntityNum.fromAccountId(newId));
        }
    }

    /**
     * Removes any aliases added to the {@link AliasManager} map as part of provisional creations.
     *
     * @return whether any aliases were removed
     */
    @Override
    public boolean reclaimPendingAliases() {
        if (!pendingCreations.isEmpty()) {
            for (final var pendingCreation : pendingCreations) {
                final var alias = pendingCreation.recordBuilder().getAlias();
                aliasManager.unlink(alias);
                if (alias.size() != EVM_ADDRESS_SIZE) {
                    aliasManager.forgetEvmAddress(alias);
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void trackSigImpactIfNeeded(
            final Builder syntheticCreation, ExpirableTxnRecord.Builder childRecord) {
        final var alias = syntheticCreation.getCryptoCreateAccount().getAlias();
        if (alias != ByteString.EMPTY) {
            sigImpactHistorian.markAliasChanged(alias);
            final var maybeAddress = aliasManager.keyAliasToEVMAddress(alias);
            if (maybeAddress != null) {
                sigImpactHistorian.markAliasChanged(ByteString.copyFrom(maybeAddress));
            }
        }
        sigImpactHistorian.markEntityChanged(childRecord.getReceiptBuilder().getAccountId().num());
    }

    public void submitRecordsTo(final RecordsHistorian recordsHistorian) {
        submitRecords(
                (syntheticBody, recordSoFar) ->
                        recordsHistorian.trackPrecedingChildRecord(
                                DEFAULT_SOURCE_ID, syntheticBody, recordSoFar));
    }

    @VisibleForTesting
    public List<InProgressChildRecord> getPendingCreations() {
        return pendingCreations;
    }

    @VisibleForTesting
    public Map<ByteString, Set<Id>> getTokenAliasMap() {
        return tokenAliasMap;
    }
}
