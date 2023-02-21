/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
 * An {@code InsufficientBalanceException} is a {@link PreCheckException} that is thrown, when the
 * balance is not sufficient. It provides the {@link #estimatedFee}.
 */
public class InsufficientBalanceException extends PreCheckException {

    private final long estimatedFee;

    /**
     * Constructor of {@code InsufficientBalanceException}
     *
     * @param responseCode the {@link ResponseCodeEnum responseCode}
     * @param estimatedFee the estimated fee
     * @throws NullPointerException if {@code responseCode} is {@code null}
     */
    public InsufficientBalanceException(@NonNull final ResponseCodeEnum responseCode, final long estimatedFee) {
        super(responseCode);
        this.estimatedFee = estimatedFee;
    }

    /**
     * Returns the estimated fee
     *
     * @return the estimated fee
     */
    public long getEstimatedFee() {
        return estimatedFee;
    }
}
