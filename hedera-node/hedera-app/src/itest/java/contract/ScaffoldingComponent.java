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

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.handlers.ContractCreateHandler;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.handle.HandlersInjectionModule;
import com.swirlds.common.metrics.Metrics;
import dagger.BindsInstance;
import dagger.Component;
import java.util.function.Function;
import javax.inject.Singleton;

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

    ContractCreateHandler contractCreateHandler();

    Function<TransactionBody, HandleContext> contextForTransaction();
}
