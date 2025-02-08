/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.fees;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.NODE;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.workflows.handle.dispatch.DispatchValidator.OfferedFeeCheck.CHECK_OFFERED_FEE;
import static com.hedera.node.app.workflows.handle.dispatch.DispatchValidator.OfferedFeeCheck.SKIP_OFFERED_FEE_CHECK;
import static com.hedera.node.app.workflows.handle.dispatch.DispatchValidator.ServiceFeeStatus.CAN_PAY_SERVICE_FEE;
import static com.hedera.node.app.workflows.handle.dispatch.DispatchValidator.ServiceFeeStatus.UNABLE_TO_PAY_SERVICE_FEE;
import static com.hedera.node.app.workflows.handle.dispatch.DispatchValidator.WorkflowCheck.NOT_INGEST;
import static com.hedera.node.app.workflows.handle.dispatch.ValidationResult.newCreatorError;
import static com.hedera.node.app.workflows.handle.dispatch.ValidationResult.newPayerError;
import static com.hedera.node.app.workflows.handle.dispatch.ValidationResult.newSuccess;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.fees.FeeCharging;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.InsufficientNonFeeDebitsException;
import com.hedera.node.app.spi.workflows.InsufficientServiceFeeException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The app's default {@link FeeCharging} strategy.
 */
@Singleton
public class AppFeeCharging implements FeeCharging {
    private final SolvencyPreCheck solvencyPreCheck;

    @Inject
    public AppFeeCharging(@NonNull final SolvencyPreCheck solvencyPreCheck) {
        this.solvencyPreCheck = requireNonNull(solvencyPreCheck);
    }

    @Override
    public Validation validate(
            @NonNull final Account payer,
            @NonNull final AccountID creatorId,
            @NonNull final Fees fees,
            @NonNull final TransactionBody body,
            boolean isDuplicate,
            @NonNull final HederaFunctionality function,
            @NonNull final HandleContext.TransactionCategory category) {
        requireNonNull(payer);
        requireNonNull(creatorId);
        requireNonNull(fees);
        requireNonNull(body);
        requireNonNull(function);
        requireNonNull(category);
        try {
            solvencyPreCheck.checkSolvency(
                    body,
                    payer.accountIdOrThrow(),
                    function,
                    payer,
                    isDuplicate ? fees : fees.withoutServiceComponent(),
                    NOT_INGEST,
                    (category == USER || category == SCHEDULED || category == NODE)
                            ? CHECK_OFFERED_FEE
                            : SKIP_OFFERED_FEE_CHECK);
        } catch (final InsufficientServiceFeeException e) {
            return newPayerError(creatorId, payer, e.responseCode(), UNABLE_TO_PAY_SERVICE_FEE, isDuplicate);
        } catch (final InsufficientNonFeeDebitsException e) {
            return newPayerError(creatorId, payer, e.responseCode(), CAN_PAY_SERVICE_FEE, isDuplicate);
        } catch (final PreCheckException e) {
            // Includes InsufficientNetworkFeeException
            return newCreatorError(creatorId, e.responseCode());
        }
        return newSuccess(creatorId, payer);
    }
}
