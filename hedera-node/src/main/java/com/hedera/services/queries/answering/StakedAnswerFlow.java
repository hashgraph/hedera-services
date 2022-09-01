/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.queries.answering;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.txns.submission.SystemPrecheck.RESTRICTED_FUNCTIONALITIES;
import static com.hedera.services.utils.MiscUtils.asTimestamp;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.queries.AnswerFlow;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.txns.submission.PlatformSubmissionManager;
import com.hedera.services.txns.submission.TransactionPrecheck;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.fee.FeeObject;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class StakedAnswerFlow implements AnswerFlow {
    private final FeeCalculator fees;
    private final QueryFeeCheck queryFeeCheck;
    private final AccountNumbers accountNums;
    private final HapiOpPermissions hapiOpPermissions;
    private final Supplier<StateView> stateViews;
    private final UsagePricesProvider resourceCosts;
    private final QueryHeaderValidity queryHeaderValidity;
    private final TransactionPrecheck transactionPrecheck;
    private final FunctionalityThrottling throttles;
    private final PlatformSubmissionManager submissionManager;

    public StakedAnswerFlow(
            final FeeCalculator fees,
            final AccountNumbers accountNums,
            final Supplier<StateView> stateViews,
            final UsagePricesProvider resourceCosts,
            final FunctionalityThrottling throttles,
            final PlatformSubmissionManager submissionManager,
            final QueryHeaderValidity queryHeaderValidity,
            final TransactionPrecheck transactionPrecheck,
            final HapiOpPermissions hapiOpPermissions,
            final QueryFeeCheck queryFeeCheck) {
        this.fees = fees;
        this.queryFeeCheck = queryFeeCheck;
        this.throttles = throttles;
        this.stateViews = stateViews;
        this.accountNums = accountNums;
        this.resourceCosts = resourceCosts;
        this.submissionManager = submissionManager;
        this.hapiOpPermissions = hapiOpPermissions;
        this.queryHeaderValidity = queryHeaderValidity;
        this.transactionPrecheck = transactionPrecheck;
    }

    @Override
    public Response satisfyUsing(final AnswerService service, final Query query) {
        final var view = stateViews.get();
        final var headerStatus = queryHeaderValidity.checkHeader(query);
        if (headerStatus != OK) {
            return service.responseGiven(query, view, headerStatus);
        }

        SignedTxnAccessor optionalPayment = null;
        final var allegedPayment = service.extractPaymentFrom(query);
        final var isPaymentRequired = service.requiresNodePayment(query);
        if (isPaymentRequired && allegedPayment.isPresent()) {
            final var signedTxn = allegedPayment.get().getSignedTxnWrapper();
            final var paymentCheck = transactionPrecheck.performForQueryPayment(signedTxn);
            final var paymentStatus = paymentCheck.getLeft().getValidity();
            if (paymentStatus != OK) {
                return service.responseGiven(query, view, paymentStatus);
            } else {
                optionalPayment = paymentCheck.getRight();
            }
        }

        final var hygieneStatus = hygieneCheck(query, view, service, optionalPayment);
        if (hygieneStatus != OK) {
            return service.responseGiven(query, view, hygieneStatus);
        }

        final var bestGuessNow =
                (null != optionalPayment)
                        ? optionalPayment.getTxnId().getTransactionValidStart()
                        : asTimestamp(Instant.now());
        final var usagePrices =
                resourceCosts.defaultPricesGiven(service.canonicalFunction(), bestGuessNow);

        long fee = 0L;
        final Map<String, Object> queryCtx = new HashMap<>();
        if (isPaymentRequired && null != optionalPayment) {
            fee = totalOf(fees.computePayment(query, usagePrices, view, bestGuessNow, queryCtx));
            final var paymentStatus = tryToPay(optionalPayment, fee);
            if (paymentStatus != OK) {
                return service.responseGiven(query, view, paymentStatus, fee);
            }
        }

        if (service.needsAnswerOnlyCost(query)) {
            fee =
                    totalOf(
                            fees.estimatePayment(
                                    query, usagePrices, view, bestGuessNow, ANSWER_ONLY));
        }

        return service.responseGiven(query, view, OK, fee, queryCtx);
    }

    private ResponseCodeEnum tryToPay(@Nonnull final SignedTxnAccessor payment, final long fee) {
        if (accountNums.isSuperuser(payment.getPayer().getAccountNum())) {
            return OK;
        }
        final var xfers =
                payment.getTxn().getCryptoTransfer().getTransfers().getAccountAmountsList();
        final var feeStatus =
                queryFeeCheck.nodePaymentValidity(xfers, fee, payment.getTxn().getNodeAccountID());
        if (feeStatus != OK) {
            return feeStatus;
        }
        return submissionManager.trySubmission(payment);
    }

    private ResponseCodeEnum hygieneCheck(
            final Query query,
            final StateView view,
            final AnswerService service,
            @Nullable final SignedTxnAccessor optionalPayment) {
        final var isPaymentRequired = service.requiresNodePayment(query);
        if (isPaymentRequired && null == optionalPayment) {
            return INSUFFICIENT_TX_FEE;
        }

        final var screenStatus = systemScreen(service.canonicalFunction(), optionalPayment, query);
        if (screenStatus != OK) {
            return screenStatus;
        }

        return service.checkValidity(query, view);
    }

    private ResponseCodeEnum systemScreen(
            final HederaFunctionality function,
            @Nullable final SignedTxnAccessor payment,
            final Query query) {
        AccountID payer = null;
        if (null != payment) {
            payer = payment.getPayer();
            final var permissionStatus = hapiOpPermissions.permissibilityOf(function, payer);
            if (permissionStatus != OK) {
                return permissionStatus;
            }
        }

        if (payer == null && RESTRICTED_FUNCTIONALITIES.contains(function)) {
            return NOT_SUPPORTED;
        } else if (payer == null || !STATIC_PROPERTIES.isThrottleExempt(payer.getAccountNum())) {
            return throttles.shouldThrottleQuery(function, query) ? BUSY : OK;
        } else {
            return OK;
        }
    }

    private long totalOf(final FeeObject costs) {
        return costs.getNetworkFee() + costs.getServiceFee() + costs.getNodeFee();
    }
}
