/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.bls.protocol;

import com.hedera.platform.bls.api.BilinearMap;

/**
 * An interface representing a BLS protocol
 *
 * @param <T> The type of the protocol output object
 */
public interface BlsProtocol<T extends ProtocolOutput> {
    /**
     * Gets the output object created by the completed protocol. Will only be valid if the protocol completed
     * successfully
     *
     * @return the output object of the protocol
     */
    T getOutput();

    /**
     * Gets the protocol manager
     *
     * @return the protocol manager
     */
    BlsProtocolManager<T> getProtocolManager();

    /**
     * Gets the BLS bilinear map being used
     *
     * @return the bilinear map
     */
    BilinearMap getBilinearMap();
}
