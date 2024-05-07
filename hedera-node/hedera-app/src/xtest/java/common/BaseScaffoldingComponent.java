/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package common;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.swirlds.config.api.Configuration;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The common provision methods needed for practically any kind of x-test, no matter the target service.
 */
public interface BaseScaffoldingComponent {
    HederaState hederaState();

    Configuration config();

    WorkingStateAccessor workingStateAccessor();

    Function<TransactionBody, HandleContext> txnContextFactory();

    Function<TransactionBody, PreHandleContext> txnPreHandleContextFactory();

    BiFunction<Query, AccountID, QueryContext> queryContextFactory();

    FeeManager feeManager();

    ExchangeRateManager exchangeRateManager();
}
