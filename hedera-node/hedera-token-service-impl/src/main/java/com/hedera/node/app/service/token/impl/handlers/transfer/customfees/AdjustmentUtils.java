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

package com.hedera.node.app.service.token.impl.handlers.transfer.customfees;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;

import com.hedera.node.app.service.mono.grpc.marshalling.BalanceChangeManager;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.math.BigInteger;

public class AdjustmentUtils {
    private AdjustmentUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static long safeFractionMultiply(final long n, final long d, final long v) {
        if (v != 0 && n > Long.MAX_VALUE / v) {
            return BigInteger.valueOf(v)
                    .multiply(BigInteger.valueOf(n))
                    .divide(BigInteger.valueOf(d))
                    .longValueExact();
        } else {
            return n * v / d;
        }
    }
}
