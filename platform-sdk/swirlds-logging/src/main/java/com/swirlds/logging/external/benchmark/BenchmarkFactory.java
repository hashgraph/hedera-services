/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.logging.external.benchmark;

import com.swirlds.logging.api.extensions.handler.LogHandlerFactory;
import com.swirlds.logging.console.ConsoleHandlerFactory;
import com.swirlds.logging.file.FileHandlerFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Class that exposes the factories, so they can be used in the benchmarking module
 */
public class BenchmarkFactory {
    private static final FileHandlerFactory FILE_FACTORY = new FileHandlerFactory();
    private static final ConsoleHandlerFactory CONSOLE_FACTORY = new ConsoleHandlerFactory();

    public static @NonNull LogHandlerFactory getConsoleHandlerFactory() {
        return CONSOLE_FACTORY;
    }

    public static @NonNull LogHandlerFactory getFileHandlerFactory() {
        return FILE_FACTORY;
    }
}
