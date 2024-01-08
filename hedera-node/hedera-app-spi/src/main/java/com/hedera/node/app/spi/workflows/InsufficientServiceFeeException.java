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

package com.hedera.node.app.spi.workflows;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An {@code InsufficientBalanceException} caused by specifically by the payer being
 * unable or unwilling to cover the network fee for a transaction.
 *
 * <p>If thrown in handle, this is a node due diligence failure. The node that submitted
 * the transaction will be charged its network fee as a penalty.
 *
 * <p>We still report the total expected fee in the {@code estimatedFee} field, however.
 */
public class InsufficientServiceFeeException extends InsufficientBalanceException {
    public InsufficientServiceFeeException(@NonNull ResponseCodeEnum responseCode, long estimatedFee) {
        super(responseCode, estimatedFee);
    }
}
