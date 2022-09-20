/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.evm.store.contracts.precompile;

import com.hedera.evm.utils.accessors.TxnAccessor;

public interface Precompile {
    default void addImplicitCostsIn(final TxnAccessor accessor) {
        // Most transaction types can compute their full Hedera fee from just an initial transaction
        // body; but
        // for a token transfer, we may need to recompute to charge for the extra work implied by
        // custom fees
    }
}
