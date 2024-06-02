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

package com.swirlds.common.service.sample.usage;

import com.swirlds.common.service.sample.api.BarService;
import com.swirlds.common.service.sample.api.BarServiceFactory;
import com.swirlds.common.service.sample.api.FooService;
import com.swirlds.common.service.sample.api.FooServiceFactory;
import com.swirlds.common.service.sample.api.FooServiceFactory.FooServiceContext;

public class SampleUsage {

    public static void main(String[] args) {
        final FooServiceFactory serviceFactory = FooServiceFactory.getInstance();
        final FooService service = serviceFactory.createService(new FooServiceContext("user"));
        final String userFoo = service.getUser();

        final BarServiceFactory barServiceFactory = BarServiceFactory.getInstance();
        final BarService barService = barServiceFactory.createService();
        final String userBar = barService.getUser();
    }
}
