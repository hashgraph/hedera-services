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

package com.hedera.node.app.blocks.translators;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.TransactionID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A grouping of block stream information used as input to record translation, where all the information is
 * linked to the same {@link TransactionID} and hence is part of the same transactional unit.
 * <p>
 * May include multiple logical HAPI transactions, and the state changes they produce.
 */
public record BlockTransactionalUnit(
        @NonNull List<BlockTransactionParts> blockTransactionParts, @NonNull List<StateChange> stateChanges) {}
