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

package com.swirlds.platform.components.common.output;

/**
 * Invoked when a fatal error has occurred. Consumers should use this method to perform any cleanup or take any final
 * actions before operations are halted.
 */
@FunctionalInterface
public interface FatalErrorConsumer {

    /**
     * A fatal error has occurred. Perform any necessary cleanup or take any final actions before operations are halted.
     *
     * @param msg
     * 		a description of the fatal error, may be {@code null}
     * @param throwable
     * 		the cause of the error, if applicable, otherwise {@code null}
     * @param exitCode
     * 		the exit code to use when shutting down the node, if applicable, otherwise {@code null}
     */
    void fatalError(final String msg, final Throwable throwable, final Integer exitCode);
}
