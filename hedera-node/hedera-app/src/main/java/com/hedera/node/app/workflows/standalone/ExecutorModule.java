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

package com.hedera.node.app.workflows.standalone;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.config.ConfigProvider;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Singleton;

@Module
public class ExecutorModule {
    private final FileServiceImpl fileService;
    private final ContractServiceImpl contractService;

    private final ConfigProviderImpl configProvider;
    private final BootstrapConfigProviderImpl bootstrapConfigProvider;

    public ExecutorModule(
            @NonNull final FileServiceImpl fileService,
            @NonNull final ContractServiceImpl contractService,
            @NonNull final ConfigProviderImpl configProvider,
            @NonNull final BootstrapConfigProviderImpl bootstrapConfigProvider) {
        this.fileService = requireNonNull(fileService);
        this.contractService = requireNonNull(contractService);
        this.configProvider = requireNonNull(configProvider);
        this.bootstrapConfigProvider = requireNonNull(bootstrapConfigProvider);
    }

    @Provides
    @Singleton
    public FileServiceImpl provideFileService() {
        return fileService;
    }

    @Provides
    @Singleton
    public ContractServiceImpl provideContractService() {
        return contractService;
    }

    @Provides
    @Singleton
    public ConfigProviderImpl provideConfigProviderImpl() {
        return configProvider;
    }

    @Provides
    @Singleton
    public ConfigProvider provideConfigProvider() {
        return configProvider;
    }

    @Provides
    @Singleton
    public BootstrapConfigProviderImpl provideBootstrapConfigProviderImpl() {
        return bootstrapConfigProvider;
    }
}
