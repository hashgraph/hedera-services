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
package com.hedera.services.txns.crypto;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.utils.accessors.custom.CryptoTransferAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoTransfer transaction, and the conditions
 * under which such logic is syntactically correct. (It is possible that the <i>semantics</i> of the
 * transaction will still be wrong; for example, if one of the accounts involved no longer has the
 * necessary funds available after consensus.)
 */
@Singleton
public class CryptoTransferTransitionLogic implements TransitionLogic {
    private final HederaLedger ledger;
    private final TransactionContext txnCtx;
    private final ImpliedTransfersMarshal impliedTransfersMarshal;
    private final ExpandHandleSpanMapAccessor spanMapAccessor;

    @Inject
    public CryptoTransferTransitionLogic(
            HederaLedger ledger,
            TransactionContext txnCtx,
            ImpliedTransfersMarshal impliedTransfersMarshal,
            ExpandHandleSpanMapAccessor spanMapAccessor) {
        this.txnCtx = txnCtx;
        this.ledger = ledger;
        this.spanMapAccessor = spanMapAccessor;
        this.impliedTransfersMarshal = impliedTransfersMarshal;
    }

    @Override
    public void doStateTransition() {
        final var accessor = (CryptoTransferAccessor) txnCtx.specializedAccessor();
        final var impliedTransfers = finalImpliedTransfersFor(accessor);

        var outcome = impliedTransfers.getMeta().code();
        validateTrue(outcome == OK, outcome);

        final var changes = impliedTransfers.getAllBalanceChanges();

        ledger.doZeroSum(changes);

        txnCtx.setAssessedCustomFees(impliedTransfers.getAssessedCustomFees());
    }

    private ImpliedTransfers finalImpliedTransfersFor(final CryptoTransferAccessor accessor) {
        var impliedTransfers = spanMapAccessor.getImpliedTransfers(accessor);
        if (impliedTransfers == null) {
            final var op = accessor.getTxn().getCryptoTransfer();
            impliedTransfers = impliedTransfersMarshal.unmarshalFromGrpc(op, accessor.getPayer());
        }
        return impliedTransfers;
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasCryptoTransfer;
    }
}
