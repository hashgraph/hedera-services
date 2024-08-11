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

package com.hedera.node.app.workflows.standalone.impl;

import com.hedera.node.app.authorization.AuthorizerInjectionModule;
import com.hedera.node.app.services.ServicesInjectionModule;
import com.hedera.node.app.state.HederaStateInjectionModule;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.throttle.ThrottleServiceModule;
import com.hedera.node.app.workflows.ExecutorModule;
import com.hedera.node.app.workflows.handle.DispatchProcessor;
import com.hedera.node.app.workflows.handle.HandleWorkflowModule;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflowInjectionModule;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.state.State;
import dagger.BindsInstance;
import dagger.Component;
import java.util.function.Consumer;
import javax.inject.Singleton;

/**
 * A component that provides DI for construction of {@link StandaloneDispatchFactory}, {@link StateNetworkInfo}, and
 * {@link DispatchProcessor} instances needed to execute standalone transactions against a {@link State}.
 */
@Singleton
@Component(
        modules = {
            StandaloneModule.class,
            HandleWorkflowModule.class,
            AuthorizerInjectionModule.class,
            PreHandleWorkflowInjectionModule.class,
            ServicesInjectionModule.class,
            HederaStateInjectionModule.class,
            ThrottleServiceModule.class,
            ExecutorModule.class
        })
public interface ExecutorComponent {
    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder metrics(Metrics metrics);

        Builder executorModule(ExecutorModule executorModule);

        ExecutorComponent build();
    }

    Consumer<State> initializer();

    DispatchProcessor dispatchProcessor();

    WorkingStateAccessor workingStateAccessor();

    StateNetworkInfo stateNetworkInfo();

    StandaloneDispatchFactory standaloneDispatchFactory();
}
