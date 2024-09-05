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


package com.hedera.cryptography.pairings.spi;

import com.hedera.cryptography.pairings.api.BilinearPairing;
import com.hedera.cryptography.pairings.api.Curve;

/**
 * A provider to facilitate fetching of a {@link BilinearPairing} instance
 */
public abstract class BilinearPairingProvider {

    /**
     * Atomic boolean so that we don't repeatedly attempt to reload the resource.
     */
    private static final AtomicBoolean initialized = new AtomicBoolean(false);


    /**
     * Gets a byte representing which curve is implemented by the provided Bilinear Pairing object
     *
     * @return a string representing the algorithm
     */
    public abstract Curve curve();

    /**
     * Returns a static instance of the bilinear map
     *
     * @return the bilinear map instance
     */
    public abstract BilinearPairing pairing();

    /**
     * Implementations should include here all the stpes necessary to load the library, e.g.,
     * perform native library loads.
     */
    public abstract void doInit() throws IOException;

    /**
     * Performs the initialization steps of the library.
     */
    public BilinearPairingProvider init() throws IOException{
        if (!initialized.getAndSet(true)) {
            loader.reload();
        }
        return this;
    }


}
