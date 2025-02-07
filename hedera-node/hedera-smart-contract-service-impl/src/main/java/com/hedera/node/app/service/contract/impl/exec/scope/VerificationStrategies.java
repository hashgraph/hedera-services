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

package com.hedera.node.app.service.contract.impl.exec.scope;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;

/**
 * Provides {@link VerificationStrategy} instances for use in signature activation tests.
 */
public interface VerificationStrategies {
    /**
     * Returns a {@link VerificationStrategy} to use based on the given sender address, delegate
     * permissions requirements, and Hedera native operations.
     *
     * @param sender the sender address
     * @param requiresDelegatePermission whether the sender is using {@code DELEGATECALL}
     * @param nativeOperations the native Hedera operations
     * @return the {@link VerificationStrategy} to use
     */
    VerificationStrategy activatingOnlyContractKeysFor(
            @NonNull Address sender,
            boolean requiresDelegatePermission,
            @NonNull HederaNativeOperations nativeOperations);
}
