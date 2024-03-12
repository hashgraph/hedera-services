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

package com.swirlds.base.internal.impl;

import com.swirlds.base.internal.BaseExecutorFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ScheduledExecutorService;

/**
 * This factory creates / provides executors for the base modules. The factory should only be used by code in the base
 * modules that highly needs an asynchronous executor. All executors that are created by this factory are daemon threads
 * and have a low priority.
 */
public class BaseExecutorFactoryImpl implements BaseExecutorFactory {

    private static final BaseExecutorFactory instance = new BaseExecutorFactoryImpl();

    private BaseExecutorFactoryImpl() {}

    @NonNull
    @Override
    public ScheduledExecutorService getScheduledExecutor() {
        return BaseScheduledExecutorService.getInstance();
    }

    /**
     * Returns the singleton instance of this factory.
     *
     * @return the instance
     */
    @NonNull
    public static BaseExecutorFactory getInstance() {
        return instance;
    }
}
