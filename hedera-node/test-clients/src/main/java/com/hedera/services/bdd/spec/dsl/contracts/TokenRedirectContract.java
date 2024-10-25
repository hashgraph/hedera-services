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

package com.hedera.services.bdd.spec.dsl.contracts;

/**
 * Enumerates the contracts that a Hedera native token "implements" by redirecting to a well-known
 * system contract address.
 */
public enum TokenRedirectContract {
    HRC("HRC"),
    // TODO: Update this to HRC904 once all tests are merged
    HRC904CLAIM("HRC904TokenClaim"),
    HRC904CANCEL("HRC904TokenCancel"),
    ERC20("ERC20ABI"),
    ERC721("ERC721ABI");

    TokenRedirectContract(String abiResource) {
        this.abiResource = abiResource;
    }

    /**
     * Returns the name of the resource with the ABI for this redirect contract.
     * @return the resource name
     */
    public String abiResource() {
        return abiResource;
    }

    private final String abiResource;
}
