/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hedera.node.app.service.consensus.impl.util.ConsensusApproveAllowanceHelper.applyCryptoAllowances;
import static com.hedera.node.app.service.consensus.impl.util.ConsensusApproveAllowanceHelper.applyFungibleTokenAllowances;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.validators.ConsensusAllowancesValidator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONSENSUS_APPROVE_ALLOWANCE}.
 */
@Singleton
public class ConsensusApproveAllowanceHandler implements TransactionHandler {
    private final ConsensusAllowancesValidator validator;

    /**
     * Default constructor for injection.
     * @param allowancesValidator allowances validator
     */
    @Inject
    public ConsensusApproveAllowanceHandler(@NonNull final ConsensusAllowancesValidator allowancesValidator) {
        requireNonNull(allowancesValidator);
        this.validator = allowancesValidator;
    }

    @Override
    public void preHandle(@NonNull PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var txn = context.body();
        final var payerId = context.payer();
        final var op = txn.consensusApproveAllowanceOrThrow();

        for (final var allowance : op.consensusCryptoFeeScheduleAllowances()) {
            final var owner = allowance.owner();
            if (owner != null && !owner.equals(payerId)) {
                context.requireKeyOrThrow(owner, INVALID_ALLOWANCE_OWNER_ID);
            }
        }

        for (final var allowance : op.consensusTokenFeeScheduleAllowances()) {
            final var owner = allowance.owner();
            if (owner != null && !owner.equals(payerId)) {
                context.requireKeyOrThrow(owner, INVALID_ALLOWANCE_OWNER_ID);
            }
        }
    }

    @Override
    public void pureChecks(@NonNull TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.consensusApproveAllowanceOrThrow();
        validator.pureChecks(op);
    }

    @Override
    public void handle(@NonNull HandleContext handleContext) throws HandleException {
        requireNonNull(handleContext, "The argument 'context' must not be null");

        final var topicStore = handleContext.storeFactory().writableStore(WritableTopicStore.class);
        final var op = handleContext.body().consensusApproveAllowanceOrThrow();

        validator.validateSemantics(handleContext, op, topicStore);

        // Apply all changes to the state modifications. We need to look up payer for each modification, since payer
        // would have been modified by a previous allowance change
        approveAllowance(handleContext, topicStore);
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        // TODO: Implement this method
        return Fees.FREE;
    }

    /**
     * Apply all changes to the state modifications for crypto and token allowances.
     * @param context the handle context
     * @param topicStore the topic store
     * @throws HandleException if there is an error applying the changes
     */
    private void approveAllowance(@NonNull final HandleContext context, @NonNull final WritableTopicStore topicStore) {
        requireNonNull(context);
        requireNonNull(topicStore);

        final var op = context.body().consensusApproveAllowanceOrThrow();
        final var cryptoAllowances = op.consensusCryptoFeeScheduleAllowances();
        final var tokenAllowances = op.consensusTokenFeeScheduleAllowances();

        /* --- Apply changes to state --- */
        applyCryptoAllowances(cryptoAllowances, topicStore);
        applyFungibleTokenAllowances(tokenAllowances, topicStore);
    }
}
