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

package com.swirlds.config.api.validation;

import com.swirlds.config.api.Configuration;
import java.util.stream.Stream;

/**
 * A validator to validate the configuration at initialization.
 */
@FunctionalInterface
public interface ConfigValidator {

    /**
     * Returns a {@link Stream} of possible violations as a result of the validation. If no violation happens the stream
     * will be empty
     *
     * @param configuration
     * 		the configuration
     * @return the violations
     */
    Stream<ConfigViolation> validate(final Configuration configuration);
}
