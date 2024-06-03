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

package contract;

import com.hedera.node.app.service.contract.impl.exec.processors.HasTranslatorsModule;
import com.hedera.node.app.service.contract.impl.exec.processors.HtsTranslatorsModule;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.CallTranslator;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.workflows.handle.HandlersInjectionModule;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import common.BaseScaffoldingComponent;
import common.BaseScaffoldingModule;
import dagger.BindsInstance;
import dagger.Component;
import java.util.List;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Used by Dagger2 to instantiate an object graph with just the roots needed for testing
 * {@link com.hedera.node.app.service.contract.ContractService} handlers.
 */
@Singleton
@Component(
        modules = {
            HandlersInjectionModule.class,
            BaseScaffoldingModule.class,
            HtsTranslatorsModule.class,
            HasTranslatorsModule.class
        })
public interface ContractScaffoldingComponent extends BaseScaffoldingComponent {
    @Component.Factory
    interface Factory {
        ContractScaffoldingComponent create(
                @BindsInstance Metrics metrics,
                @BindsInstance Configuration configuration,
                @BindsInstance StoreMetricsService storeMetricsService);
    }

    @Named("HtsTranslators")
    List<CallTranslator> callHtsTranslators();

    @Named("HasTranslators")
    List<CallTranslator> callHasTranslators();
}
