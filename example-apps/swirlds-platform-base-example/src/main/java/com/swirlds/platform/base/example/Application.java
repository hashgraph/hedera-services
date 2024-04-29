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

import com.swirlds.platform.base.example.executorsample.BaseExecutorHandlerFactory;
import com.swirlds.platform.base.example.ext.BaseContext;
import com.swirlds.platform.base.example.ext.BaseContextFactory;
import com.swirlds.platform.base.example.jdkmetrics.JVMInternalMetrics;
import com.swirlds.platform.base.example.metricsample.MetricsSampleHandlerRegistry;
import com.swirlds.platform.base.example.server.Server;
import com.swirlds.platform.base.example.store.StoreExampleHandlerRegistry;
import java.io.IOException;

/**
 * This application serves as a testing environment for platform-base module frameworks.
 */
public class Application {

    public static void main(String[] args) throws IOException {
        final BaseContext baseContext = BaseContextFactory.create();
        // Add JDK metrics to track memory, cpu, etc
        JVMInternalMetrics.registerMetrics(baseContext.metrics());
        Server.start(
                baseContext,
                new StoreExampleHandlerRegistry(),
                new MetricsSampleHandlerRegistry(),
                new BaseExecutorHandlerFactory());
    }
}
