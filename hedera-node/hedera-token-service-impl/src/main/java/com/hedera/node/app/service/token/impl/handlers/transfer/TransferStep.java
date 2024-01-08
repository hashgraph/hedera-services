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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import com.hedera.hapi.node.base.Key;
import java.util.Set;

/**
 * Defines the interface for each step in the crypto transfer process.
 */
public interface TransferStep {
    /**
     * Returns the set of keys that are authorized to perform this step.
     * @param transferContext the context of the transfer
     * @return the set of keys that are authorized to perform this step
     */
    // FUTURE: all the logic in prehandle can be moved into appropriate steps
    default Set<Key> authorizingKeysIn(TransferContext transferContext) {
        return Set.of();
    }

    /**
     * Perform the step and commit changes to the modifications in state.
     * @param transferContext the context of the transfer
     * @throws com.hedera.node.app.spi.workflows.HandleException if the step fails
     */
    void doIn(TransferContext transferContext);
}
