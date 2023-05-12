/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform.nft.config;

/**
 * Config for an NFT test.
 */
public class NftConfig {

    private NftQueryConfig queryConfig;

    public NftQueryConfig getQueryConfig() {
        return queryConfig;
    }

    /**
     * Set the configuration for NFT queries.
     *
     * @param queryConfig
     * 		the query config object
     */
    public void setQueryConfig(final NftQueryConfig queryConfig) {
        this.queryConfig = queryConfig;
    }
}
