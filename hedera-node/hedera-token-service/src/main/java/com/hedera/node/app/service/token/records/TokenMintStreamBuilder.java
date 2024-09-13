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

package com.hedera.node.app.service.token.records;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A {@code StreamBuilder} specialization for tracking the side effects of a {@code CryptoCreate}
 * transaction.
 */
public interface TokenMintStreamBuilder extends TokenBaseStreamBuilder {
    /**
     * Tracks creation of a new account by number. Even if someday we support creating multiple
     * accounts within a smart contract call, we will still only need to track one created account
     * per child record.
     *
     * @param serialNumbers the list of new serial numbers minted
     * @return this builder
     */
    @NonNull
    TokenMintStreamBuilder serialNumbers(@NonNull List<Long> serialNumbers);

    /**
     * Sets the new total supply of a token.
     * @param newTotalSupply the new total supply of a token
     * @return this builder
     */
    TokenMintStreamBuilder newTotalSupply(long newTotalSupply);

    /**
     * Gets the new total supply of a token.
     * @return new total supply of a token
     */
    long getNewTotalSupply();
}
