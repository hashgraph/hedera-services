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
package com.hedera.services.txns;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

@Singleton
public class TransitionRunner {
    private static final Logger log = LogManager.getLogger(TransitionRunner.class);

    /**
     * Some operation's transition logic still explicitly set SUCCESS instead of letting the runner
     * handle this.
     */
    private static final EnumSet<HederaFunctionality> opsWithDefaultSuccessStatus =
            EnumSet.of(
                    TokenMint,
                    TokenBurn,
                    TokenFreezeAccount,
                    TokenUnfreezeAccount,
                    TokenGrantKycToAccount,
                    TokenRevokeKycFromAccount,
                    TokenAssociateToAccount,
                    TokenDissociateFromAccount,
                    TokenAccountWipe,
                    TokenCreate,
                    TokenPause,
                    TokenUnpause,
                    TokenFeeScheduleUpdate,
                    CryptoTransfer,
                    ConsensusCreateTopic,
                    ContractDelete,
                    TokenDelete,
                    Freeze,
                    FileDelete,
                    CryptoApproveAllowance,
                    UtilPrng);

    private final EntityIdSource ids;
    private final TransactionContext txnCtx;
    private final TransitionLogicLookup lookup;

    @Inject
    public TransitionRunner(
            final EntityIdSource ids,
            final TransactionContext txnCtx,
            final TransitionLogicLookup lookup) {
        this.ids = ids;
        this.txnCtx = txnCtx;
        this.lookup = lookup;
    }

    /**
     * Tries to find and run transition logic for the transaction wrapped by the given accessor.
     *
     * @param accessor the transaction accessor
     * @return true if the logic was run to completion
     */
    public boolean tryTransition(@NotNull final TxnAccessor accessor) {
        final var txn = accessor.getTxn();
        final var function = accessor.getFunction();
        final var logic = lookup.lookupFor(function, txn);
        if (logic.isEmpty()) {
            log.warn(
                    "Transaction w/o applicable transition logic at consensus :: {}",
                    accessor::getSignedTxnWrapper);
            txnCtx.setStatus(FAIL_INVALID);
            return false;
        } else {
            final var transition = logic.get();
            final var validity = transition.validateSemantics(accessor);
            if (validity != OK) {
                txnCtx.setStatus(validity);
                return false;
            }

            try {
                transition.doStateTransition();
                if (opsWithDefaultSuccessStatus.contains(function)) {
                    txnCtx.setStatus(SUCCESS);
                }
            } catch (final InvalidTransactionException e) {
                resolveFailure(e, accessor);
            } catch (final Exception processFailure) {
                ids.reclaimProvisionalIds();
                throw processFailure;
            }
            return true;
        }
    }

    private void resolveFailure(final InvalidTransactionException e, final TxnAccessor accessor) {
        final var code = e.getResponseCode();
        if (code == FAIL_INVALID) {
            log.warn("Avoidable failure while handling {}", accessor.getSignedTxnWrapper(), e);
        }
        txnCtx.setStatus(code);
        ids.reclaimProvisionalIds();
    }
}
