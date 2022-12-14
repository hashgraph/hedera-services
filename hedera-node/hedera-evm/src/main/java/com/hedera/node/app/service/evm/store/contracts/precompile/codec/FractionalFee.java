/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import org.hyperledger.besu.datatypes.Address;

public class FractionalFee {

    private long numerator;
    private long denominator;
    private long getMinimumAmount;
    private long getMaximumAmount;
    private boolean netOfTransfers;
    private Address feeCollector;

    public FractionalFee(
            long numerator,
            long denominator,
            long getMinimumAmount,
            long getMaximumAmount,
            boolean netOfTransfers,
            Address feeCollector) {
        this.numerator = numerator;
        this.denominator = denominator;
        this.getMinimumAmount = getMinimumAmount;
        this.getMaximumAmount = getMaximumAmount;
        this.netOfTransfers = netOfTransfers;
        this.feeCollector = feeCollector;
    }
}
