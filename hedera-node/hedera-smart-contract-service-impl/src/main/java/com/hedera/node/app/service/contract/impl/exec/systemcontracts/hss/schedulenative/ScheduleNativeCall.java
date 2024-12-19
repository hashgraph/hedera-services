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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.schedulenative;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.RC_AND_ADDRESS_ENCODER;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CallType.DIRECT_OR_PROXY_REDIRECT;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallFactory;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.spi.workflows.DispatchOptions.UsePresetTxnId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class ScheduleNativeCall extends AbstractCall {

    private final VerificationStrategy verificationStrategy;
    private final AccountID payerID;
    private final DispatchGasCalculator dispatchGasCalculator;
    private final Set<Key> authorizingKeys;
    private final Bytes innerCallData;
    private final boolean waitForExpiry;
    private final HtsCallFactory htsCallFactory;

    public ScheduleNativeCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final AccountID payerID,
            @NonNull final DispatchGasCalculator dispatchGasCalculator,
            @NonNull final Set<Key> authorizingKeys,
            @NonNull final Bytes innerCallData,
            @NonNull final HtsCallFactory htsCallFactory,
            final boolean waitForExpiry) {
        super(gasCalculator, enhancement, false);
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.payerID = requireNonNull(payerID);
        this.dispatchGasCalculator = requireNonNull(dispatchGasCalculator);
        this.authorizingKeys = authorizingKeys;
        this.innerCallData = requireNonNull(innerCallData);
        this.htsCallFactory = requireNonNull(htsCallFactory);
        this.waitForExpiry = waitForExpiry;
    }

    @Override
    @NonNull
    public PricedResult execute(@NonNull final MessageFrame frame) {
        // Create the native call implied by the call data passed to scheduleNative()
        final var nativeAttempt = htsCallFactory.createCallAttemptFrom(innerCallData, DIRECT_OR_PROXY_REDIRECT, frame);
        final var call = requireNonNull(nativeAttempt.asExecutableCall());
        final var scheduleTransactionBody = call.asSchedulableDispatchIn();
        final var scheduleCreateTransactionBody = bodyForScheduleCreate(scheduleTransactionBody);
        final var gasRequirement = dispatchGasCalculator.gasRequirement(
                scheduleCreateTransactionBody, gasCalculator, enhancement, payerID);
        final var recordBuilder = systemContractOperations()
                .dispatch(
                        scheduleCreateTransactionBody,
                        verificationStrategy,
                        payerID,
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

    private @NonNull TransactionBody bodyForScheduleCreate(SchedulableTransactionBody scheduleTransactionBody) {
        return TransactionBody.newBuilder()
                .transactionID(nativeOperations().getTransactionID())
                .scheduleCreate(ScheduleCreateTransactionBody.newBuilder()
                        .scheduledTransactionBody(scheduleTransactionBody)
                        .payerAccountID(payerID)
                        .waitForExpiry(waitForExpiry))
                .build();
    }
}
