/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.token.api.FeeStreamBuilder;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.fees.Fees;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Accumulates fees for a given transaction. They can either be charged to a payer account, ore refunded to a receiver
 * account.
 */
public class FeeAccumulator {
    private final TokenServiceApi tokenApi;
    private final FeeStreamBuilder recordBuilder;

    /**
     * Creates a new instance of {@link FeeAccumulator}.
     *
     * @param tokenApi the {@link TokenServiceApi} to use to charge and refund fees.
     * @param recordBuilder the {@link FeeStreamBuilder} to record any changes
     */
    public FeeAccumulator(@NonNull final TokenServiceApi tokenApi, @NonNull final FeeStreamBuilder recordBuilder) {
        this.tokenApi = requireNonNull(tokenApi);
        this.recordBuilder = requireNonNull(recordBuilder);
    }

    /**
     * Charges the given network fee to the given payer account.
     *
     * @param payer The account to charge the fees to
     * @param networkFee The network fee to charge
     * @return true if the full fee was charged
     */
    public boolean chargeNetworkFee(@NonNull final AccountID payer, final long networkFee) {
        requireNonNull(payer);
        return tokenApi.chargeNetworkFee(payer, networkFee, recordBuilder);
    }

    /**
     * Charges the given fees to the given payer account, distributing the network and service fees among the
     * appropriate collection accounts; and the node fee (if any) to the given node account.
     *
     * @param payer The account to charge the fees to
     * @param nodeAccount The node account to receive the node fee
     * @param fees The fees to charge
     */
    public void chargeFees(@NonNull AccountID payer, @NonNull final AccountID nodeAccount, @NonNull Fees fees) {
        requireNonNull(payer);
        requireNonNull(nodeAccount);
        requireNonNull(fees);
        tokenApi.chargeFees(payer, nodeAccount, fees, recordBuilder);
    }

    /**
     * Refunds the given fees to the receiver account.
     *
     * @param receiver The account to refund the fees to.
     * @param fees The fees to refund.
     */
    public void refund(@NonNull AccountID receiver, @NonNull Fees fees) {
        tokenApi.refundFees(receiver, fees, recordBuilder);
    }
}
