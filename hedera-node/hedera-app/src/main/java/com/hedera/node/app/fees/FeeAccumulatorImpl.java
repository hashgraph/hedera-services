/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.token.api.FeeRecordBuilder;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.Fees;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Default implementation of {@link FeeAccumulator}.
 */
public class FeeAccumulatorImpl implements FeeAccumulator {
    private final TokenServiceApi tokenApi;
    private final FeeRecordBuilder recordBuilder;

    /**
     * Creates a new instance of {@link FeeAccumulatorImpl}.
     *
     * @param tokenApi the {@link TokenServiceApi} to use to charge and refund fees.
     * @param recordBuilder the {@link FeeRecordBuilder} to record any changes
     */
    public FeeAccumulatorImpl(@NonNull final TokenServiceApi tokenApi, @NonNull final FeeRecordBuilder recordBuilder) {
        this.tokenApi = tokenApi;
        this.recordBuilder = recordBuilder;
    }

    @Override
    public void charge(@NonNull AccountID payer, @NonNull Fees fees) {
        tokenApi.chargeFees(payer, fees, recordBuilder);
    }

    @Override
    public void refund(@NonNull AccountID receiver, @NonNull Fees fees) {
        tokenApi.refundFees(receiver, fees, recordBuilder);
    }
}
