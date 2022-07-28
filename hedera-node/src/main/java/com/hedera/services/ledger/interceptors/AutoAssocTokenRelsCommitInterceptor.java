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
package com.hedera.services.ledger.interceptors;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.CommitInterceptor;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.lang3.tuple.Pair;

/** Interceptor that externalizes any auto-associations created during a transaction. */
public class AutoAssocTokenRelsCommitInterceptor
        implements CommitInterceptor<
                Pair<AccountID, TokenID>, MerkleTokenRelStatus, TokenRelProperty> {
    private static final Set<HederaFunctionality> AUTO_ASSOCIATING_OPS =
            EnumSet.of(CryptoTransfer, TokenCreate);

    // If null, every new association is interpreted as an auto-association; if non-null,
    // associations
    // are only auto-associations if the active transaction type is in AUTO_ASSOCIATING_OPS
    @Nullable private final TransactionContext txnCtx;
    protected final SideEffectsTracker sideEffectsTracker;

    public static AutoAssocTokenRelsCommitInterceptor forKnownAutoAssociatingOp(
            final SideEffectsTracker sideEffectsTracker) {
        return new AutoAssocTokenRelsCommitInterceptor(null, sideEffectsTracker);
    }

    public AutoAssocTokenRelsCommitInterceptor(
            final @Nullable TransactionContext txnCtx,
            final SideEffectsTracker sideEffectsTracker) {
        this.txnCtx = txnCtx;
        this.sideEffectsTracker = sideEffectsTracker;
    }

    /** {@inheritDoc} */
    @Override
    public void preview(
            final EntityChangeSet<Pair<AccountID, TokenID>, MerkleTokenRelStatus, TokenRelProperty>
                    pendingChanges) {
        if (txnCtx != null && activeOpIsNotAutoAssociating()) {
            return;
        }
        for (int i = 0, n = pendingChanges.retainedSize(); i < n; i++) {
            // A null current entity means this is a new association
            if (pendingChanges.entity(i) == null) {
                final var id = pendingChanges.id(i);
                sideEffectsTracker.trackAutoAssociation(id.getRight(), id.getLeft());
            }
        }
    }

    private boolean activeOpIsNotAutoAssociating() {
        return !AUTO_ASSOCIATING_OPS.contains(txnCtx.accessor().getFunction());
    }
}
