/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.properties;

import static com.hedera.services.context.properties.SemanticVersions.SEMANTIC_VERSIONS;

import com.hedera.services.context.annotations.CompositeProps;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public interface PropertiesModule {
    @Provides
    @Singleton
    @CompositeProps
    static PropertySource providePropertySource(PropertySources propertySources) {
        return propertySources.asResolvingSource();
    }

    @Provides
    @Singleton
    static SemanticVersions provideSemanticVersions() {
        return SEMANTIC_VERSIONS;
    }

    @Binds
    @Singleton
    PropertySources bindPropertySources(StandardizedPropertySources standardizedPropertySources);
}
