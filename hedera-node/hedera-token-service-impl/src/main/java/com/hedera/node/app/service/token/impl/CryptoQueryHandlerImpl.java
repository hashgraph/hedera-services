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

package com.hedera.node.app.service.token.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.token.CryptoGetAccountBalanceQuery;
import com.hedera.hapi.node.token.CryptoGetAccountRecordsQuery;
import com.hedera.hapi.node.token.GetAccountDetailsQuery;
import com.hedera.hapi.node.transaction.TransactionGetReceiptQuery;
import com.hedera.hapi.node.transaction.TransactionGetRecordQuery;
import com.hedera.node.app.service.token.CryptoQueryHandler;
import com.hedera.node.app.service.token.entity.Account;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import org.apache.commons.lang3.NotImplementedException;

/** Default implementation of {@link CryptoQueryHandler} */
public class CryptoQueryHandlerImpl implements CryptoQueryHandler {

    /**
     * Constructor of {@code CryptoQueryHandlerImpl}
     *
     * @param states the {@link ReadableStates} object
     * @throws NullPointerException if {@code states} is {@code null}
     */
    public CryptoQueryHandlerImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);
    }

    @Override
    public Optional<Account> getAccountById(@NonNull final AccountID id) {
        throw new NotImplementedException();
    }

    @Override
    public void getAccountRecords(@NonNull final CryptoGetAccountRecordsQuery query) {
        throw new NotImplementedException();
    }

    @Override
    public void cryptoGetBalance(@NonNull final CryptoGetAccountBalanceQuery query) {
        throw new NotImplementedException();
    }

    @Override
    public void getAccountInfo(@NonNull final GetAccountDetailsQuery query) {
        throw new NotImplementedException();
    }

    @Override
    public void getTransactionReceipts(@NonNull final TransactionGetReceiptQuery query) {
        throw new NotImplementedException();
    }

    @Override
    public void getTxRecordByTxID(@NonNull final TransactionGetRecordQuery query) {
        throw new NotImplementedException();
    }
}
