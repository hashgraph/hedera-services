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

package com.hedera.node.app.service.token;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.node.app.spi.metrics.ServiceMetrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Interface defining metrics managed by the {@link TokenService}
 */
public interface ExampleTokenMetrics extends ServiceMetrics {

    /**
     * Records the minting of a List of NFT serial numbers, and aggregates the total number of serials minted
     *
     * @param tokenID the token ID the serials are associated with
     * @param serials the serial numbers of the minted NFTs
     */
    void incrementBySerialsCreated(@NonNull final TokenID tokenID, @NonNull final List<Long> serials);
}
