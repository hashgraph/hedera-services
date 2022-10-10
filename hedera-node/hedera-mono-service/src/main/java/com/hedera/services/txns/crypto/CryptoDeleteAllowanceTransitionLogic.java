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
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoDeleteAllowance transaction, and the
 * conditions under which such logic is syntactically correct.
 */
public class CryptoDeleteAllowanceTransitionLogic implements TransitionLogic {
    private final TransactionContext txnCtx;
    private final AccountStore accountStore;
    private final DeleteAllowanceChecks deleteAllowanceChecks;
    private final StateView workingView;
    private final DeleteAllowanceLogic deleteAllowanceLogic;

    @Inject
    public CryptoDeleteAllowanceTransitionLogic(
            final TransactionContext txnCtx,
            final AccountStore accountStore,
            final DeleteAllowanceChecks deleteAllowanceChecks,
            final StateView workingView,
            final DeleteAllowanceLogic deleteAllowanceLogic) {
        this.txnCtx = txnCtx;
        this.accountStore = accountStore;
        this.deleteAllowanceChecks = deleteAllowanceChecks;
        this.workingView = workingView;
        this.deleteAllowanceLogic = deleteAllowanceLogic;
    }

    @Override
    public void doStateTransition() {
        /* --- Extract gRPC --- */
        final TransactionBody cryptoDeleteAllowanceTxn = txnCtx.accessor().getTxn();
        final AccountID payer = txnCtx.activePayer();
        final var op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

        deleteAllowanceLogic.deleteAllowance(op.getNftAllowancesList(), payer);

        txnCtx.setStatus(SUCCESS);
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasCryptoDeleteAllowance;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    private ResponseCodeEnum validate(TransactionBody cryptoDeleteAllowanceTxn) {
        final AccountID payer = cryptoDeleteAllowanceTxn.getTransactionID().getAccountID();
        final var op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
        final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(payer));

        return deleteAllowanceChecks.deleteAllowancesValidation(
                op.getNftAllowancesList(), payerAccount, workingView);
    }
}
