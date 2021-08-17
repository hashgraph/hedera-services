package com.hedera.services.legacy;

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

import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.builder.RequestBuilder;

/** @author Akshay @Date : 8/20/2018 */
public class TestHelper {
  private static long DEFAULT_WIND_SEC = -13; // seconds to wind back the UTC clock
  private static volatile long lastNano = 0;

  /** Gets the current UTC timestamp with default winding back seconds. */
  public static synchronized Timestamp getDefaultCurrentTimestampUTC() {
    Timestamp rv = ProtoCommonUtils.getCurrentTimestampUTC(DEFAULT_WIND_SEC);
    if (rv.getNanos() == lastNano) {
      try {
        Thread.sleep(0, 1);
      } catch (InterruptedException e) {
      }
      rv = ProtoCommonUtils.getCurrentTimestampUTC(DEFAULT_WIND_SEC);
      lastNano = rv.getNanos();
    }
    return rv;
  }

  public static Transaction createTransferUnsigned(
      AccountID fromAccount,
      AccountID toAccount,
      AccountID payerAccount,
      AccountID nodeAccount,
      long amount) {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(30);

    Transaction transferTx =
        RequestBuilder.getCryptoTransferRequest(
            payerAccount.getAccountNum(),
            payerAccount.getRealmNum(),
            payerAccount.getShardNum(),
            nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(),
            nodeAccount.getShardNum(),
            50,
            timestamp,
            transactionDuration,
            false,
            "Test Transfer",
            fromAccount.getAccountNum(),
            -amount,
            toAccount.getAccountNum(),
            amount);

    return transferTx;
  }
}
