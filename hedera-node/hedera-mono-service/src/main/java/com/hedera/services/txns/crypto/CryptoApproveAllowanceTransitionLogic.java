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
package com.hedera.services.txns.crypto;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoApproveAllowance transaction, and the
 * conditions under which such logic is syntactically correct.
 */
public class CryptoApproveAllowanceTransitionLogic implements TransitionLogic {
    private final TransactionContext txnCtx;
    private final AccountStore accountStore;
    private final ApproveAllowanceChecks allowanceChecks;
    private final ApproveAllowanceLogic approveAllowanceLogic;
    private final StateView workingView;

    @Inject
    public CryptoApproveAllowanceTransitionLogic(
            final TransactionContext txnCtx,
            final AccountStore accountStore,
            final ApproveAllowanceChecks allowanceChecks,
            final ApproveAllowanceLogic approveAllowanceLogic,
            final StateView workingView) {
        this.txnCtx = txnCtx;
        this.accountStore = accountStore;
        this.allowanceChecks = allowanceChecks;
        this.approveAllowanceLogic = approveAllowanceLogic;
        this.workingView = workingView;
    }

    @Override
    public void doStateTransition() {
        /* --- Extract gRPC --- */
        final TransactionBody cryptoApproveAllowanceTxn = txnCtx.accessor().getTxn();
        final AccountID payer = txnCtx.activePayer();
        final var op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        approveAllowanceLogic.approveAllowance(
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                payer);

        txnCtx.setStatus(SUCCESS);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasCryptoApproveAllowance;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    private ResponseCodeEnum validate(TransactionBody cryptoAllowanceTxn) {
        final AccountID payer = cryptoAllowanceTxn.getTransactionID().getAccountID();
        final var op = cryptoAllowanceTxn.getCryptoApproveAllowance();
        final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(payer));

        return allowanceChecks.allowancesValidation(
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                payerAccount,
                workingView);
    }
}
