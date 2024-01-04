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

package com.swirlds.platform.roster;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Describes the difference between two rosters. Although it is possible to derive this information by comparing the
 * {@link AddressBook} objects directly, this data is distilled and provided in this format for convenience.
 *
 * @param consensusWeightChanged whether the consensus weight changed
 * @param membershipChanged      whether the membership changed
 * @param addedNodes             the nodes that were added
 * @param removedNodes           the nodes that were removed
 * @param modifiedNodes          the nodes that were modified
 */
public record RosterDiff(
        @NonNull UpdatedRoster newRoster,
        boolean rosterIsIdentical,
        boolean consensusWeightChanged,
        boolean membershipChanged,
        @NonNull List<NodeId> addedNodes,
        @NonNull List<NodeId> removedNodes,
        @NonNull List<NodeId> modifiedNodes) {}
