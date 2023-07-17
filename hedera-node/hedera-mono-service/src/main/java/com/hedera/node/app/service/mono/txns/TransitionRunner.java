/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.txns;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UtilPrng;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.context.AppsManager;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.ledger.HederaLedger;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.state.virtual.IterableStorageUtils;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class TransitionRunner implements TransactionLastStep {
    private static final Logger log = LogManager.getLogger(TransitionRunner.class);

    /**
     * Some operation's transition logic still explicitly set SUCCESS instead of letting the runner
     * handle this.
     */
    private static final EnumSet<HederaFunctionality> opsWithDefaultSuccessStatus = EnumSet.of(
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

    protected final EntityIdSource ids;
    private final TransitionLogicLookup lookup;
    protected final TransactionContext txnCtx;

    @Inject
    public TransitionRunner(
            final EntityIdSource ids, final TransactionContext txnCtx, final TransitionLogicLookup lookup) {
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
    public boolean tryTransition(@NonNull final TxnAccessor accessor) {
        final var txn = accessor.getTxn();
        final var function = accessor.getFunction();
        final var logic = lookup.lookupFor(function, txn);
        if (logic.isEmpty()) {
            log.warn("Transaction w/o applicable transition logic at consensus :: {}", accessor::getSignedTxnWrapper);
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
                if (function == ContractCall) {
                    final var app = AppsManager.APPS.get(new NodeId(0));
                    final var accounts = app.backingAccounts();
                    final var orderedIds = new TreeSet<>(HederaLedger.ACCOUNT_ID_COMPARATOR);
                    orderedIds.addAll(accounts.idSet());
                    orderedIds.forEach(id -> {
                        final var account = accounts.getRef(id);
                        if (account.isSmartContract()) {
                            final var storage = app.workingState().contractStorage();
                            final var firstKey = account.getFirstContractStorageKey();
                            System.out.println("Contract storage for 0.0." + id.getAccountNum() + ":");
                            System.out.println(IterableStorageUtils.joinedStorageMappings(firstKey, storage));
                        }
                    });
                }
            } catch (final InvalidTransactionException e) {
                resolveFailure(e.getResponseCode(), accessor, e);
            } catch (final Exception processFailure) {
                ids.reclaimProvisionalIds();
                throw processFailure;
            }
            return true;
        }
    }

    protected void resolveFailure(final ResponseCodeEnum code, final TxnAccessor accessor, final RuntimeException e) {
        if (code == FAIL_INVALID) {
            log.warn("Avoidable failure while handling {}", accessor.getSignedTxnWrapper(), e);
        }
        txnCtx.setStatus(code);
        ids.reclaimProvisionalIds();
    }
}
