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

package com.hedera.node.app.service.consensus;

import com.hedera.hapi.node.base.TopicAllowanceId;
import com.hedera.hapi.node.base.TopicAllowanceValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Topic Allowances.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public interface ReadableTopicAllowanceStore {

    /**
     * Returns the topic allowance needed. If the topic allowance doesn't exist returns null.
     *
     * @param allowanceId topic allowance id being looked up
     * @return topic allowance value
     */
    @Nullable
    TopicAllowanceValue get(@NonNull TopicAllowanceId allowanceId);

    /**
     * Returns true if the topic allowance exists.
     *
     * @param allowanceId topic allowance id being looked up
     * @return true if the topic allowance exists
     */
    boolean exists(@NonNull TopicAllowanceId allowanceId);

    /**
     * Returns the number of topic allowances in the state.
     * @return the number of topic allowances in the state
     */
    long sizeOfState();
}
