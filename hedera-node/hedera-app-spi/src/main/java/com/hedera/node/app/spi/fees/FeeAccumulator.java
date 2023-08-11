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

package com.hedera.node.app.spi.fees;

import com.hedera.hapi.node.base.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Accumulates fees for a given transaction. They can either be charged to a payer account, ore refunded to a receiver
 * account.
 */
public interface FeeAccumulator {
    /**
     * Charges the given fees to the given payer account.
     *
     * @param payer The account to charge the fees to.
     * @param fees The fees to charge.
     */
    void charge(@NonNull AccountID payer, @NonNull Fees fees);

    /**
     * Refunds the given fees to the receiver account.
     *
     * @param receiver The account to refund the fees to.
     * @param fees The fees to refund.
     */
    void refund(@NonNull AccountID receiver, @NonNull Fees fees);
}
