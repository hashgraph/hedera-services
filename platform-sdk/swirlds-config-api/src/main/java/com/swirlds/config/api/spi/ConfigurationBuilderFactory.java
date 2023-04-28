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

package com.swirlds.config.api.spi;

import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This is the SPI (see {@link java.util.ServiceLoader}) interface that an implementation of the config API needs to
 * provide.
 */
public interface ConfigurationBuilderFactory {

    /**
     * By calling this method a new {@link ConfigurationBuilder} instance is created and returned.
     *
     * @return a new {@link ConfigurationBuilder} instance
     */
    @NonNull
    ConfigurationBuilder create();
}
