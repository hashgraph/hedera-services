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

package com.hedera.node.app.bbm.contracts;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;

/**
 * All contracts extracted from a signed state file
 *
 * @param contracts - dictionary of contract bytecodes indexed by their contract id (as a Long)
 * @param deletedContracts - collection of ids of deleted contracts
 * @param registeredContractsCount - total #contracts known to the _accounts_ in the signed
 *     state file (not all actually have bytecodes in the file store, and of those, some have
 *     0-length bytecode files)
 */
public record Contracts(
        @NonNull Collection</*@NonNull*/ Contract> contracts,
        @NonNull Collection<Integer> deletedContracts,
        int registeredContractsCount) {}
