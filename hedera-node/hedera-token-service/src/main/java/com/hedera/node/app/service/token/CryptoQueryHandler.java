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
package com.hedera.node.app.service.token;

import com.hedera.node.app.service.token.entity.Account;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.GetAccountDetailsQuery;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptQuery;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

/**
 * Defines APIs for responding to queries on state. Most of these APIs are defined in
 * "CryptoService" in the protobuf. Some APIs are defined for the use of other modules, or the
 * Hedera application. Some queries are paid, some are free.
 */
public interface CryptoQueryHandler {
    /**
     * Retrieves an {@link Account} given an {@link AccountID}. This method is not defined in
     * "CryptoService", but exists for the use of other modules, including the Hedera application.
     *
     * @param id The id. Cannot be null.
     * @return A non-null {@link Optional} with a reference to the {@link Account}, or empty if
     *     there is not one matching the given ID.
     */
    Optional<Account> getAccountById(@NonNull AccountID id);

    /**
     * Returns all transactions within the persistence period of consensus time for which the given
     * account was the effective payer <b>and</b> network property {@code ledger.keepRecordsInState}
     * was {@code true}.
     */
    void getAccountRecords(@NonNull CryptoGetAccountRecordsQuery query);

    /** Retrieves the balance of an account */
    void cryptoGetBalance(@NonNull CryptoGetAccountBalanceQuery query);

    /** Retrieves the metadata of an account */
    void getAccountInfo(@NonNull GetAccountDetailsQuery query);

    /**
     * Retrieves the latest receipt for a transaction that is either awaiting consensus, or reached
     * consensus in the last 180 seconds
     */
    void getTransactionReceipts(@NonNull TransactionGetReceiptQuery query);

    /**
     * Retrieves the record of a transaction that is either awaiting consensus, or reached consensus
     * in the last 180 seconds
     */
    void getTxRecordByTxID(@NonNull TransactionGetRecordQuery query);
}
