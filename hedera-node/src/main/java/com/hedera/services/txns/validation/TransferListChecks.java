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
package com.hedera.services.txns.validation;

import static java.math.BigInteger.ZERO;

import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.api.proto.java.TransferListOrBuilder;
import java.math.BigInteger;

/**
 * Offers a few static helpers to evaluate {@link TransferList} instances presented by incoming gRPC
 * transactions.
 */
public final class TransferListChecks {
    private TransferListChecks() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static boolean isNetZeroAdjustment(final TransferListOrBuilder wrapper) {
        var net = ZERO;
        for (final var adjustment : wrapper.getAccountAmountsOrBuilderList()) {
            net = net.add(BigInteger.valueOf(adjustment.getAmount()));
        }
        return net.equals(ZERO);
    }
}
