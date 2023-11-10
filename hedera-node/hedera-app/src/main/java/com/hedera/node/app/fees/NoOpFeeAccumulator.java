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
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.Fees;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A no-op implementation of {@link FeeAccumulator} that can be used if no fees are to be charged.
 */
public class NoOpFeeAccumulator implements FeeAccumulator {

    public static final NoOpFeeAccumulator INSTANCE = new NoOpFeeAccumulator();

    @Override
    public void chargeFees(@NonNull AccountID payer, AccountID nodeAccount, @NonNull Fees fees) {}

    @Override
    public boolean chargeNetworkFee(@NonNull AccountID payer, long networkFee) {
        return false;
    }

    @Override
    public void refund(@NonNull AccountID receiver, @NonNull Fees fees) {}
}
