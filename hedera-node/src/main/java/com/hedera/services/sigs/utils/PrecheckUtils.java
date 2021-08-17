package com.hedera.services.sigs.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Predicate;

/**
 * Contains static helpers used during precheck to validate signatures.
 *
 * @author Michael Tinker
 */
public class PrecheckUtils {
  /**
   * Constructs a predicate testing whether a {@link TransactionBody} should be considered a query
   * payment for the given node.
   *
   * @param deservingNode the id of a node's account.
   * @return a predicate testing if a txn is a query payment for the given node.
   */
  public static Predicate<TransactionBody> queryPaymentTestFor(AccountID deservingNode) {
    return txn ->
        txn.hasCryptoTransfer()
            && txn.getCryptoTransfer().getTransfers().getAccountAmountsList().stream()
                .filter(aa -> aa.getAmount() > 0)
                .map(AccountAmount::getAccountID)
                .anyMatch(deservingNode::equals);
  }
}
