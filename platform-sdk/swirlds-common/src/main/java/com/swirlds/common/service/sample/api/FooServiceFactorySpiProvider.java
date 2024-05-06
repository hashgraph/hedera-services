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

package com.swirlds.common.service.sample.api;

import com.swirlds.common.service.api.AbstractSpiBasedServiceFactoryProvider;
import com.swirlds.common.service.sample.api.FooServiceFactory.FooServiceContext;
import edu.umd.cs.findbugs.annotations.NonNull;

public class FooServiceFactorySpiProvider
        extends AbstractSpiBasedServiceFactoryProvider<FooServiceContext, FooService, FooServiceFactory> {

    private static final class InstanceHolder {
        private static final FooServiceFactorySpiProvider INSTANCE = new FooServiceFactorySpiProvider();
    }

    protected FooServiceFactorySpiProvider() {
        super(FooServiceFactory.class);
    }

    @NonNull
    public static FooServiceFactorySpiProvider getInstance() {
        return InstanceHolder.INSTANCE;
    }
}
