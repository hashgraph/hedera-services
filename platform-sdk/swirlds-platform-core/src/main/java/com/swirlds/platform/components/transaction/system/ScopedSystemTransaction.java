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

package com.swirlds.platform.components.transaction.system;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A system transaction with a submitter ID and a software version. The submitter ID is not included with the
 * transaction, it is determined by the event that the transaction is contained within. This is intentional, as it makes
 * it impossible for a transaction to lie and claim to be submitted by a node that did not actually submit it.
 *
 * @param submitterId     the ID of the node that submitted the transaction
 * @param softwareVersion the software version of the event that contained the transaction
 * @param transaction     the transaction
 * @param <T>             the type of transaction
 */
public record ScopedSystemTransaction<T>(
        @NonNull NodeId submitterId, @Nullable SemanticVersion softwareVersion, @NonNull T transaction) {}
