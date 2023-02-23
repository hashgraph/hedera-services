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
package com.hedera.node.app.components;

import com.hedera.node.app.annotations.MaxSignedTxnSize;
import com.hedera.node.app.platform.PlatformModule;
import com.hedera.node.app.service.mono.config.ConfigModule;
import com.hedera.node.app.service.mono.context.ContextModule;
import com.hedera.node.app.service.mono.context.annotations.BootstrapProps;
import com.hedera.node.app.service.mono.context.properties.PropertiesModule;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.fees.FeesModule;
import com.hedera.node.app.service.mono.records.RecordsModule;
import com.hedera.node.app.service.mono.state.StateModule;
import com.hedera.node.app.service.mono.stats.StatsModule;
import com.hedera.node.app.service.mono.throttling.ThrottlingModule;
import com.hedera.node.app.services.ServiceModule;
import com.hedera.node.app.workflows.query.QueryModule;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.swirlds.common.system.Platform;
import dagger.BindsInstance;
import dagger.Component;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Singleton;

@Singleton
@Component(
        modules = {
                FeesModule.class,
                QueryModule.class,
                StatsModule.class,
                StateModule.class,
                ConfigModule.class,
                ServiceModule.class,
                RecordsModule.class,
                ContextModule.class,
                PlatformModule.class,
                PropertiesModule.class,
                ThrottlingModule.class
        })
public interface QueryComponent {
    QueryWorkflow queryWorkflow();

    @Component.Factory
    interface Factory {
        QueryComponent create(
                @BindsInstance @BootstrapProps final PropertySource bootstrapProps,
                @BindsInstance @MaxSignedTxnSize final int maxSignedTxnSize,
                @BindsInstance final @NonNull Platform platform);
    }
}
