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

package contract;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.workflows.handle.HandleContextImpl;
import com.hedera.node.app.workflows.handle.HandlersInjectionModule;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.swirlds.common.metrics.Metrics;
import dagger.BindsInstance;
import dagger.Component;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Used by Dagger2 to instantiate an object graph with three roots:
 * <ol>
 *     <li>An empty {@link FakeHederaState} that a test can populate with
 *     its preconditions (e.g., next entity number) and well-known entities.</li>
 *     <li>A function mapping a {@link TransactionBody} to a
 *     {@link HandleContext} based on the above {@link FakeHederaState};
 *     tests can pass this context to a {@link TransactionHandler}
 *     implementation for the {@link TransactionBody} with no additional
 *     use of mocks.</li>
 *     <li>The singleton {@link SavepointStackImpl} based on the
 *     {@link FakeHederaState} that a test can use to commit changes
 *     after calling its handler.</li>
 * </ol>
 *
 * <p>Whenever the dependency graph of {@link HandleContextImpl}
 * expands with new bindings that Dagger2 cannot construct via
 * {@link Inject}-able constructors, this class will need to be
 * updated, either with a new {@link dagger.Module} that provides
 * the new bindings; or with additions to the existing
 * {@link ScaffoldingModule}.
 */
@Singleton
@Component(
        modules = {
            HandlersInjectionModule.class,
            ScaffoldingModule.class,
        })
public interface ScaffoldingComponent {
    @Component.Factory
    interface Factory {
        ScaffoldingComponent create(@BindsInstance Metrics metrics);
    }

    HederaState hederaState();

    WorkingStateAccessor workingStateAccessor();

    Function<TransactionBody, HandleContext> txnContextFactory();

    BiFunction<Query, AccountID, QueryContext> queryContextFactory();

    FeeManager feeManager();
}
