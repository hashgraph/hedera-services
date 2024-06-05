/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.dispatcher;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.handle.flow.DueDiligenceInfo;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import java.time.Instant;
import java.util.Set;

public interface Dispatch {

    SingleTransactionRecordBuilderImpl recordBuilder();

    Fees calculatedFees();

    TransactionInfo txnInfo();

    AccountID syntheticPayer();

    ReadableStoreFactory storeFactory();

    DueDiligenceInfo dueDiligenceInfo();

    FeeAccumulator feeAccumulator();

    KeyVerifier keyVerifier();

    NodeInfo creatorInfo();

    Instant consensusNow();

    Set<Key> requiredKeys();

    Set<Account> hollowAccounts();

    ResponseCodeEnum userError();
}
