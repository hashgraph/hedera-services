/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.utility;

import java.util.concurrent.Callable;

/**
 * Same as {@link Runnable} but can throw an {@link Exception}. Also extends {@link Callable} for convenience.
 */
@FunctionalInterface
public interface ThrowingRunnable extends Callable<Void> {
    /**
     * Execute this runnable
     *
     * @throws Exception
     * 		if any issue occurs
     */
    void run() throws Exception;

    /**
     * {@inheritDoc}
     */
    @Override
    default Void call() throws Exception {
        run();
        return null;
    }
}
