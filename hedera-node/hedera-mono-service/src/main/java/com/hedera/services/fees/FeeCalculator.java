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
package com.hedera.services.fees;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.RenewAssessment;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.fee.FeeObject;
import java.time.Instant;
import java.util.Map;

/**
 * Defines a type able to calculate the fees required for various operations within Hedera Services.
 */
public interface FeeCalculator {
    void init();

    long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at);

    long estimatedNonFeePayerAdjustments(TxnAccessor accessor, Timestamp at);

    FeeObject computeFee(
            TxnAccessor accessor, JKey payerKey, StateView view, Instant consensusTime);

    FeeObject estimateFee(TxnAccessor accessor, JKey payerKey, StateView view, Timestamp at);

    FeeObject estimatePayment(
            Query query, FeeData usagePrices, StateView view, Timestamp at, ResponseType type);

    FeeObject computePayment(
            Query query,
            FeeData usagePrices,
            StateView view,
            Timestamp at,
            Map<String, Object> queryCtx);

    /**
     * Assesses the longest period for which the expired account can afford to renew itself, up to
     * the requested period; as well as the service fee to be charged for renewing the account for
     * that period.
     *
     * <p><b>Important:</b> The fee charged will always be <i>calculated</i> for a period that is a
     * multiple of 3600 seconds, because the fee schedule uses hours as the units to price memory
     * consumption.
     *
     * <p>However, the assessed renewal period will still be (up to) the requested renewal, even if
     * it is not an exact multiple of 3600. Fees are rounded <i>up</i> to the nearest hour.
     *
     * @param expiredAccount the expired account
     * @param requestedRenewal the desired renewal period
     * @param now the consensus time of expiration
     * @return the corresponding RenewAssessment
     */
    RenewAssessment assessCryptoAutoRenewal(
            HederaAccount expiredAccount, long requestedRenewal, Instant now, HederaAccount payer);
}
