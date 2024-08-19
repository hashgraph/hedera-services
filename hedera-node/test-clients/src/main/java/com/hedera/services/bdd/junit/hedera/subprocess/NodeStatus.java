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

package com.hedera.services.bdd.junit.hedera.subprocess;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents the status of a node in the network.
 *
 * @param lastAttempt the last attempt to look up the status of the node
 * @param grpcStatus the last known gRPC status of the node
 * @param bindExceptionSeen whether a bind exception has been seen in the logs
 * @param retryCount the number of times the status lookup has been retried
 */
public record NodeStatus(
        @NonNull StatusLookupAttempt lastAttempt,
        @NonNull GrpcStatus grpcStatus,
        @NonNull BindExceptionSeen bindExceptionSeen,
        int retryCount) {
    public enum GrpcStatus {
        NA,
        UP,
        DOWN
    }

    public enum BindExceptionSeen {
        NA,
        YES,
        NO
    }
}
