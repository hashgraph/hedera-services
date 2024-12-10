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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.scheduledcreate;

import static com.hedera.hapi.node.base.HederaFunctionality.SCHEDULE_CREATE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasPlus;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.RC_AND_ADDRESS_ENCODER;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.ClassicCreatesCall.FIXED_GAS_COST;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.spi.workflows.DispatchOptions.UsePresetTxnId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class ScheduledCreateCall extends AbstractCall {

    private final VerificationStrategy verificationStrategy;
    private final TransactionBody syntheticScheduleCreate;
    private final AccountID sender;
    private final DispatchGasCalculator dispatchGasCalculator;
    private final Set<Key> authorizingKeys;

    public ScheduledCreateCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final TransactionBody syntheticScheduleCreate,
            @NonNull final AccountID sender,
            @NonNull final DispatchGasCalculator dispatchGasCalculator,
            @NonNull final Set<Key> authorizingKeys) {
        super(gasCalculator, enhancement, false);
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.syntheticScheduleCreate = syntheticScheduleCreate;
        this.sender = requireNonNull(sender);
        this.dispatchGasCalculator = requireNonNull(dispatchGasCalculator);
        this.authorizingKeys = authorizingKeys;
    }

    @Override
    public PricedResult execute(@NonNull final MessageFrame frame) {
        // Reusing some of the logic for token create in order to calculate the fees required for the scheduled
        // transaction
        // and do the necessary checks before its execution.
        // This includes the gas cost for the inner token create + non-gas sent via {msg.value}.

        final var costOfInnerCreate = gasCalculator.feeCalculatorPriceInTinyBars(
                wrapIntoSyntheticTokenCreateTransactionBody(syntheticScheduleCreate), sender);
        final long nonGasCostOfInnerCreate =
                (costOfInnerCreate + (costOfInnerCreate / 5)) - gasCalculator.gasCostInTinybars(FIXED_GAS_COST);
        if (frame.getValue().lessThan(Wei.of(nonGasCostOfInnerCreate))) {
            // If the sender haven't provided enough funds to cover the inner token create, we preempt the dispatch
            return completionWith(
                    FIXED_GAS_COST,
                    systemContractOperations()
                            .externalizePreemptedDispatch(
                                    syntheticScheduleCreate, INSUFFICIENT_TX_FEE, SCHEDULE_CREATE),
                    RC_AND_ADDRESS_ENCODER.encodeElements((long) INSUFFICIENT_TX_FEE.protoOrdinal(), ZERO_ADDRESS));
        } else {
            operations().collectFee(sender, nonGasCostOfInnerCreate);
        }

        final var validStatus = validateTokenField(syntheticScheduleCreate);
        if (validStatus != OK) {
            return gasOnly(revertResult(validStatus, FIXED_GAS_COST), validStatus, false);
        }

        final var recordBuilder = systemContractOperations()
                .dispatch(
                        syntheticScheduleCreate,
                        verificationStrategy,
                        sender,
                        ContractCallStreamBuilder.class,
                        authorizingKeys,
                        UsePresetTxnId.YES);

        final var status = recordBuilder.status();
        final var gasRequirement =
                dispatchGasCalculator.gasRequirement(syntheticScheduleCreate, gasCalculator, enhancement, sender);
        if (status != SUCCESS) {
            return reversionWith(status, gasRequirement);
        } else {
            final var encodedRes = RC_AND_ADDRESS_ENCODER.encodeElements(
                    (long) SUCCESS.protoOrdinal(), headlongAddressOf(recordBuilder.scheduleID()));
            return gasPlus(
                    successResult(encodedRes, gasRequirement + FIXED_GAS_COST, recordBuilder),
                    status,
                    false,
                    nonGasCostOfInnerCreate);
        }
    }

    /**
     * Wraps the given {@link com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody} into a synthetic token create transaction body.
     *
     * @param transactionBody the transaction body
     * @return the synthetic token create transaction body
     */
    private TransactionBody wrapIntoSyntheticTokenCreateTransactionBody(
            @NonNull final TransactionBody transactionBody) {
        final var tokenCreateTransactionBody = getTokenCreateTransactionBody(transactionBody);
        return TransactionBody.newBuilder()
                .tokenCreation(tokenCreateTransactionBody)
                .build();
    }

    private ResponseCodeEnum validateTokenField(@NonNull final TransactionBody transactionBody) {
        final var tokenCreateTransactionBody = getTokenCreateTransactionBody(transactionBody);
        if (tokenCreateTransactionBody.symbol().isEmpty()) {
            return MISSING_TOKEN_SYMBOL;
        }
        if (tokenCreateTransactionBody.name().isEmpty()) {
            return MISSING_TOKEN_NAME;
        }
        final var treasury = nativeOperations().getAccount(tokenCreateTransactionBody.treasury());
        if (treasury == null) {
            return INVALID_ACCOUNT_ID;
        }
        return OK;
    }

    private static TokenCreateTransactionBody getTokenCreateTransactionBody(@NonNull TransactionBody transactionBody) {
        return requireNonNull(transactionBody.scheduleCreate()).hasScheduledTransactionBody()
                ? requireNonNull(
                                requireNonNull(transactionBody.scheduleCreate()).scheduledTransactionBody())
                        .tokenCreation()
                : TokenCreateTransactionBody.DEFAULT;
    }
}
