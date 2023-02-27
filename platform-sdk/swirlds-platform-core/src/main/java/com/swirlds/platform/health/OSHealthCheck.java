/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.health;

import com.swirlds.common.config.OSHealthCheckConfig;

/**
 * Performs an OS health check and reports the results
 */
@FunctionalInterface
public interface OSHealthCheck {

    /**
     * Performs a single OS health check and reports the result to a {@link StringBuilder}.
     *
     * @param sb
     * 		the string builder to append the report to
     * @param config
     * 		config values for determining if a check passes or fails
     * @return {@code true} if the check passes, {@code false} otherwise
     */
    boolean performCheckAndReport(StringBuilder sb, OSHealthCheckConfig config);
}
