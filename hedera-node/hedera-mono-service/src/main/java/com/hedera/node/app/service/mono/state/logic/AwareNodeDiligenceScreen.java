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

import static com.hedera.node.app.service.mono.txns.diligence.DuplicateClassification.NODE_DUPLICATE;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.readableId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.txns.diligence.DuplicateClassification;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public final class AwareNodeDiligenceScreen {
    private static final Logger log = LogManager.getLogger(AwareNodeDiligenceScreen.class);

    private static final String WRONG_NODE_LOG_TPL =
            "Node {} (member #{}) submitted a txn meant for node account {} :: {}";
    private static final String MISSING_NODE_LOG_TPL =
            "Node {} (member #{}) submitted a txn w/ missing node account {} :: {}";

    private final OptionValidator validator;
    private final TransactionContext txnCtx;
    private final BackingStore<AccountID, HederaAccount> backingAccounts;

    @Inject
    public AwareNodeDiligenceScreen(
            final OptionValidator validator,
            final TransactionContext txnCtx,
            final BackingStore<AccountID, HederaAccount> backingAccounts) {
        this.txnCtx = txnCtx;
        this.validator = validator;
        this.backingAccounts = backingAccounts;
    }

    public boolean nodeIgnoredDueDiligence(final DuplicateClassification duplicity) {
        final var accessor = txnCtx.accessor();
        // We don't want a transaction with unknown protobuf fields to resolve to
        // SUCCESS, because it may contain fields (e.g. staking elections) that the
        // mirror nodes already support, leading them to become confused about the
        // actual state of the world; note that a well-behaved node will always
        // reject such transactions in precheck, so we charge the submitting node
        // a penalty here for lack of due diligence
        if (accessor.hasConsequentialUnknownFields()) {
            txnCtx.setStatus(TRANSACTION_HAS_UNKNOWN_FIELDS);
            return true;
        }

        final var submittingAccount = txnCtx.submittingNodeAccount();
        final var designatedAccount = accessor.getTxn().getNodeAccountID();
        final var designatedNodeExists = backingAccounts.contains(designatedAccount);
        if (!designatedNodeExists) {
            logAccountWarning(
                    MISSING_NODE_LOG_TPL,
                    submittingAccount,
                    txnCtx.submittingSwirldsMember(),
                    designatedAccount,
                    accessor);
            txnCtx.setStatus(INVALID_NODE_ACCOUNT);
            return true;
        }

        final var payerAccountId = accessor.getPayer();
        final var payerAccountExists = backingAccounts.contains(payerAccountId);

        if (!payerAccountExists) {
            txnCtx.setStatus(ACCOUNT_ID_DOES_NOT_EXIST);
            return true;
        }

        final var payerAccountRef = backingAccounts.getImmutableRef(payerAccountId);

        if (payerAccountRef.isDeleted()) {
            txnCtx.setStatus(PAYER_ACCOUNT_DELETED);
            return true;
        }

        if (!submittingAccount.equals(designatedAccount)) {
            logAccountWarning(
                    WRONG_NODE_LOG_TPL,
                    submittingAccount,
                    txnCtx.submittingSwirldsMember(),
                    designatedAccount,
                    accessor);
            txnCtx.setStatus(INVALID_NODE_ACCOUNT);
            return true;
        }

        if (!txnCtx.isPayerSigKnownActive()) {
            txnCtx.setStatus(INVALID_PAYER_SIGNATURE);
            return true;
        }

        if (duplicity == NODE_DUPLICATE) {
            txnCtx.setStatus(DUPLICATE_TRANSACTION);
            return true;
        }

        final var txnDuration = accessor.getTxn().getTransactionValidDuration().getSeconds();
        if (!validator.isValidTxnDuration(txnDuration)) {
            txnCtx.setStatus(INVALID_TRANSACTION_DURATION);
            return true;
        }

        final var cronStatus = validator.chronologyStatus(accessor, txnCtx.consensusTime());
        if (cronStatus != OK) {
            txnCtx.setStatus(cronStatus);
            return true;
        }

        final var memoValidity =
                validator.rawMemoCheck(accessor.getMemoUtf8Bytes(), accessor.memoHasZeroByte());
        if (memoValidity != OK) {
            txnCtx.setStatus(memoValidity);
            return true;
        }

        return false;
    }

    /**
     * Logs account warnings
     *
     * @param message template for the log which includes each of the additional parameters
     * @param submittingNodeAccount submitting node account for the transaction
     * @param submittingMember submitting member
     * @param relatedAccount related account as to which the warning applies to
     * @param accessor transaction accessor
     */
    @SuppressWarnings("java:S2629")
    private void logAccountWarning(
            final String message,
            final AccountID submittingNodeAccount,
            final long submittingMember,
            final AccountID relatedAccount,
            final TxnAccessor accessor) {
        log.warn(
                message,
                readableId(submittingNodeAccount),
                submittingMember,
                readableId(relatedAccount),
                accessor.getSignedTxnWrapper());
    }
}
