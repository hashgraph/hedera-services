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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.RC_AND_ADDRESS_ENCODER;
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

        final var gasRequirement =
                dispatchGasCalculator.gasRequirement(syntheticScheduleCreate, gasCalculator, enhancement, sender);

        // Add inner transaction validation before dispatching to the ScheduleService
        final var validStatus = validateTokenField(syntheticScheduleCreate);
        if (validStatus != OK) {
            return gasOnly(revertResult(validStatus, gasRequirement), validStatus, false);
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
        if (status != SUCCESS) {
            return gasOnly(revertResult(status, gasRequirement), status, false);
        } else {
            final var encodedRes = RC_AND_ADDRESS_ENCODER.encodeElements(
                    (long) SUCCESS.protoOrdinal(), headlongAddressOf(recordBuilder.scheduleID()));
            return gasOnly(successResult(encodedRes, gasRequirement, recordBuilder), status, false);
        }
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
