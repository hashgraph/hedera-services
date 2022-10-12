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
package com.hedera.services.txns.ethereum;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.ledger.properties.AccountProperty.ETHEREUM_NONCE;
import static com.hedera.services.legacy.proto.utils.ByteStringUtils.wrapUnsafely;
import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.WRONG_CHAIN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.WRONG_NONCE;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.SynthCreationCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.PreFetchableTransition;
import com.hedera.services.txns.contract.ContractCallTransitionLogic;
import com.hedera.services.txns.contract.ContractCreateTransitionLogic;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.txns.span.SpanMapManager;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class EthereumTransitionLogic implements PreFetchableTransition {
    private static final Logger log = LogManager.getLogger(EthereumTransitionLogic.class);
    private static final TransactionBody INVALID_SYNTH_BODY = TransactionBody.getDefaultInstance();

    private final AliasManager aliasManager;
    private final SpanMapManager spanMapManager;
    private final TransactionContext txnCtx;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final SynthCreationCustomizer creationCustomizer;
    private final GlobalDynamicProperties dynamicProperties;
    private final TransactionRecordService recordService;
    private final ExpandHandleSpanMapAccessor spanMapAccessor;
    private final ContractCallTransitionLogic contractCallTransitionLogic;
    private final ContractCreateTransitionLogic contractCreateTransitionLogic;
    private final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;

    @Inject
    public EthereumTransitionLogic(
            final TransactionContext txnCtx,
            final SyntheticTxnFactory syntheticTxnFactory,
            final SynthCreationCustomizer creationCustomizer,
            final ExpandHandleSpanMapAccessor spanMapAccessor,
            final ContractCallTransitionLogic contractCallTransitionLogic,
            final ContractCreateTransitionLogic contractCreateTransitionLogic,
            final TransactionRecordService recordService,
            final GlobalDynamicProperties dynamicProperties,
            final AliasManager aliasManager,
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger,
            final SpanMapManager spanMapManager) {
        this.txnCtx = txnCtx;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.creationCustomizer = creationCustomizer;
        this.spanMapAccessor = spanMapAccessor;
        this.contractCallTransitionLogic = contractCallTransitionLogic;
        this.contractCreateTransitionLogic = contractCreateTransitionLogic;
        this.recordService = recordService;
        this.aliasManager = aliasManager;
        this.accountsLedger = accountsLedger;
        this.dynamicProperties = dynamicProperties;
        this.spanMapManager = spanMapManager;
    }

    @Override
    public void doStateTransition() {
        // Collect everything from the context
        final var accessor = txnCtx.accessor();
        final var callerNum = validatedCallerOf(accessor);
        final var synthTxn = spanMapAccessor.getEthTxBodyMeta(accessor);
        final var ethTxData = spanMapAccessor.getEthTxDataMeta(accessor);
        // we don't support 2930 transactions, even though we parse them.
        validateFalse(
                ethTxData.type() == EthTxData.EthTransactionType.EIP2930,
                INVALID_ETHEREUM_TRANSACTION);
        final var relayerId = Id.fromGrpcAccount(accessor.getPayer());
        final var maxGasAllowance = accessor.getTxn().getEthereumTransaction().getMaxGasAllowance();
        final var userOfferedGasPrice = ethTxData.getMaxGasAsBigInteger();

        // Revoke the relayer's key for Ethereum operations
        txnCtx.swirldsTxnAccessor().getSigMeta().revokeCryptoSigsFrom(txnCtx.activePayerKey());
        if (synthTxn.hasContractCall()) {
            delegateToCallTransition(
                    callerNum.toId(), synthTxn, relayerId, maxGasAllowance, userOfferedGasPrice);
        } else {
            delegateToCreateTransition(
                    callerNum.toId(), synthTxn, relayerId, maxGasAllowance, userOfferedGasPrice);
        }

        recordService.updateForEvmCall(
                spanMapAccessor.getEthTxDataMeta(accessor), callerNum.toEntityId());
    }

    @Override
    public ResponseCodeEnum validateSemantics(final TxnAccessor accessor) {
        if (accessor.getTxn().getEthereumTransaction().getMaxGasAllowance() < 0) {
            return NEGATIVE_ALLOWANCE_AMOUNT;
        }
        final var ethTxData = spanMapAccessor.getEthTxDataMeta(accessor);
        if (ethTxData == null) {
            return INVALID_ETHEREUM_TRANSACTION;
        }
        if (!ethTxData.matchesChainId(dynamicProperties.chainIdBytes())) {
            return WRONG_CHAIN_ID;
        }
        // This is always set inside handleTransaction
        final var ethTxExpansion = spanMapAccessor.getEthTxExpansion(accessor);
        final var isPrecheck = ethTxExpansion == null;

        var txn = spanMapAccessor.getEthTxBodyMeta(accessor);
        if (txn == null) {
            txn =
                    isPrecheck
                            ? syntheticTxnFactory.synthPrecheckContractOpFromEth(ethTxData)
                            : INVALID_SYNTH_BODY;
        }
        if (txn.hasContractCall()) {
            return contractCallTransitionLogic.semanticCheck().apply(txn);
        } else if (txn.hasContractCreateInstance()) {
            return contractCreateTransitionLogic.semanticCheck().apply(txn);
        } else {
            // Could only happen in handleTransaction, so short-circuit to the pre-computed failure
            // in the expansion
            return Objects.requireNonNull(ethTxExpansion).result();
        }
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasEthereumTransaction;
    }

    @Override
    public void preFetch(final TxnAccessor accessor) {
        try {
            spanMapManager.expandEthereumSpan(accessor);
        } catch (Exception e) {
            log.warn("Pre-fetch failed for {}", accessor.getSignedTxnWrapper(), e);
        }
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        throw new UnsupportedOperationException();
    }

    // --- Internal helpers ---
    private void delegateToCallTransition(
            final Id callerId,
            final TransactionBody synthCall,
            final Id relayerId,
            final long maxGasAllowance,
            final BigInteger offeredGasPrice) {
        contractCallTransitionLogic.doStateTransitionOperation(
                synthCall, callerId, relayerId, maxGasAllowance, offeredGasPrice);
    }

    private void delegateToCreateTransition(
            final Id callerId,
            final TransactionBody synthCreate,
            final Id relayerId,
            final long maxGasAllowance,
            final BigInteger offeredGasPrice) {
        final var customizedCreate =
                creationCustomizer.customize(synthCreate, callerId.asGrpcAccount());
        contractCreateTransitionLogic.doStateTransitionOperation(
                customizedCreate, callerId, true, relayerId, maxGasAllowance, offeredGasPrice);
    }

    private EntityNum validatedCallerOf(final TxnAccessor accessor) {
        // We take advantage of the validation work done by SpanMapManager, which guaranteed that a
        // EthTxExpansion exists in the span map; and that if its result is OK, the EthTxData,
        // EthTxSigs,
        // and synthetic TransactionBody _also_ exist in the span map
        final var expansion = Objects.requireNonNull(spanMapAccessor.getEthTxExpansion(accessor));
        validateTrue(expansion.result() == OK, expansion.result());

        final var ethTxSigs = spanMapAccessor.getEthTxSigsMeta(accessor);
        final var callerNum = aliasManager.lookupIdBy(wrapUnsafely(ethTxSigs.address()));
        validateTrue(callerNum != MISSING_NUM, INVALID_ACCOUNT_ID);

        final var ethTxData = spanMapAccessor.getEthTxDataMeta(accessor);
        final var expectedNonce =
                (long) accountsLedger.get(callerNum.toGrpcAccountId(), ETHEREUM_NONCE);
        validateTrue(expectedNonce == ethTxData.nonce(), WRONG_NONCE);

        return callerNum;
    }
}
