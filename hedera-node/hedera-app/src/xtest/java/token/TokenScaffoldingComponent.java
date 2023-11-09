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

package token;

import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenMintHandler;
import com.hedera.node.app.workflows.handle.HandlersInjectionModule;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.config.api.Configuration;
import common.BaseScaffoldingComponent;
import common.BaseScaffoldingModule;
import dagger.BindsInstance;
import dagger.Component;
import javax.inject.Singleton;

/**
 * Used by Dagger2 to instantiate an object graph with just the roots needed for testing
 * {@link com.hedera.node.app.service.token.TokenService} handlers.
 */
@Singleton
@Component(modules = {HandlersInjectionModule.class, BaseScaffoldingModule.class})
public interface TokenScaffoldingComponent extends BaseScaffoldingComponent {
    @Component.Factory
    interface Factory {
        TokenScaffoldingComponent create(@BindsInstance Metrics metrics, @BindsInstance Configuration configuration);
    }

    CryptoTransferHandler cryptoTransferHandler();

    TokenMintHandler tokenMintHandler();
}
