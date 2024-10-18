/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A functional interface for submitting status actions
 */
@FunctionalInterface
public interface StatusActionSubmitter {
    /**
     * Submit a status action, which will be processed in the order received
     *
     * @param action the action to submit
     */
    void submitStatusAction(@NonNull final PlatformStatusAction action);
}
