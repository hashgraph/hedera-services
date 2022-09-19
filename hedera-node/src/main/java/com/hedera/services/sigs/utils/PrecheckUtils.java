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
package com.hedera.services.sigs.utils;

import com.hedera.services.context.NodeInfo;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.function.Predicate;

/** Contains static helpers used during precheck to validate signatures. */
public final class PrecheckUtils {
    private PrecheckUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Constructs a predicate testing whether a {@link TransactionBody} should be considered a query
     * payment for the given node.
     *
     * @param nodeInfo information about the node receiving the query
     * @return a predicate testing if a txn is a query payment for the given node
     */
    public static Predicate<TransactionBody> queryPaymentTestFor(final NodeInfo nodeInfo) {
        return txn ->
                txn.hasCryptoTransfer()
                        && includesCredit(
                                txn.getCryptoTransfer().getTransfers().getAccountAmountsList(),
                                nodeInfo.selfAccount());
    }

    private static boolean includesCredit(
            final List<AccountAmount> adjusts, final AccountID beneficiary) {
        for (final var adjust : adjusts) {
            if (adjust.getAmount() > 0 && adjust.getAccountID().equals(beneficiary)) {
                return true;
            }
        }
        return false;
    }
}
