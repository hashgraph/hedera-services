/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.di;

import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.handlers.CryptoApproveAllowanceHandlerI;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.ServiceEntryPoint;
import com.hedera.node.app.spi.workflows.FreeQueryHandler;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import java.util.Set;
import javax.inject.Named;
import javax.inject.Singleton;

public class ServiceProvider {

    private static final ServiceEntryPoint serviceEntryPoint = new ServiceEntryPoint();

    @Provides
    @ElementsIntoSet
    static Set<Service> provideAllServices() {
        return serviceEntryPoint.getAllServices();
    }

    @Provides
    @Singleton
    static CryptoService provideCryptoService() {
        return serviceEntryPoint.getServiceByType(CryptoService.class)
                .orElseThrow(
                        () -> new IllegalStateException("No service of type '" + CryptoService.class + "' provided"));
    }

    @Provides
    @Singleton
    @Named("CryptoGetAccountBalanceHandler")
    static FreeQueryHandler provideCryptoGetAccountBalanceHandler() {
        return provideCryptoService().getCryptoGetAccountBalanceHandler();
    }

    @Provides
    @Singleton
    static CryptoApproveAllowanceHandlerI provideCryptoApproveAllowanceHandler() {
        return provideCryptoService().getCryptoApproveAllowanceHandler();
    }

}
