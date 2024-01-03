/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.state.output;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.state.notifications.IssNotification;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Invoked when an Invalid State Signature (ISS) is detected.
 */
@FunctionalInterface
public interface IssConsumer {

    /**
     * An ISS has occurred.
     *
     * @param round
     * 		the round of the ISS
     * @param issType
     * 		the type of ISS
     * @param issNodeId
     * 		the id of the node with the ISS, or {@code null} if it is a catastrophic ISS
     */
    void iss(long round, @NonNull IssNotification.IssType issType, @Nullable NodeId issNodeId);
}
