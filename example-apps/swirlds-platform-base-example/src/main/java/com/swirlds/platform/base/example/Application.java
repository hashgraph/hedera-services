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

package com.swirlds.platform.base.example;

import com.swirlds.platform.base.example.ext.BaseContextFactory;
import com.swirlds.platform.base.example.metricsample.MetricsSampleHandlerFactory;
import com.swirlds.platform.base.example.server.Server;
import com.swirlds.platform.base.example.store.StoreSampleHandlerFactory;
import java.io.IOException;
import java.util.Set;

/**
 * This application serves as a testing environment for platform-base module frameworks.
 */
public class Application {

    public static void main(String[] args) throws IOException {
        final BaseContext baseContext = BaseContextFactory.create();

        // Application specific metrics and data
        StoreSampleHandlerFactory storeSampleHandlerFactory = new StoreSampleHandlerFactory();

        MetricsSampleHandlerFactory metricsSampleHandlerFactory = new MetricsSampleHandlerFactory();

        Server.start(baseContext, Set.of(storeSampleHandlerFactory, metricsSampleHandlerFactory));
    }
}
