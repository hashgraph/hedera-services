/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.fees;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.List;
import java.util.function.Predicate;

public class TinyBarTransfers implements Predicate<TransferList> {
    private final TransferList transfers;

    public TinyBarTransfers(TransferList transfers) {
        this.transfers = transfers;
    }

    public TransferList getTransfers() {
        return transfers;
    }

    @Override
    public boolean test(TransferList transfers) {
        /* We currently assume that the different types of transfers
        (fee payments, CryptoTransfer transaction, SC activity)
        create separate entries in the List<AccountAmount>; and
        these entries are not consolidated. */
        return transfersContains(transfers.getAccountAmountsList(), this.transfers.getAccountAmountsList());
    }

    private boolean transfersContains(List<AccountAmount> amounts, List<AccountAmount> changes) {
        return changes.stream()
                .allMatch(c -> amounts.stream().filter(c::equals).findAny().isPresent());
    }
}
