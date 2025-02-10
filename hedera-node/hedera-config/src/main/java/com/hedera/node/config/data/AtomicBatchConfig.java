/*
<<<<<<<< HEAD:hedera-node/hedera-config/src/main/java/com/hedera/node/config/data/AtomicBatchConfig.java
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
========
 * Copyright (C) 2025 Hedera Hashgraph, LLC
>>>>>>>> main:hedera-node/hedera-app/src/main/java/com/hedera/node/app/platform/PlatformStateModule.java
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

<<<<<<<< HEAD:hedera-node/hedera-config/src/main/java/com/hedera/node/config/data/AtomicBatchConfig.java
package com.hedera.node.config.data;

import com.hedera.node.config.NetworkProperty;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("atomicBatch")
public record AtomicBatchConfig(@ConfigProperty(defaultValue = "true") @NetworkProperty boolean isEnabled) {}
========
package com.hedera.node.app.platform;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.swirlds.platform.system.SoftwareVersion;
import dagger.Module;
import dagger.Provides;
import java.util.function.Function;
import javax.inject.Singleton;

@Module
public interface PlatformStateModule {

    @Provides
    @Singleton
    static Function<SemanticVersion, SoftwareVersion> softwareVersionFactory() {
        return ServicesSoftwareVersion::new;
    }
}
>>>>>>>> main:hedera-node/hedera-app/src/main/java/com/hedera/node/app/platform/PlatformStateModule.java
