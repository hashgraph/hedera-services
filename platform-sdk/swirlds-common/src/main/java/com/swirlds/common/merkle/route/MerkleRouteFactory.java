/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.route;

import com.swirlds.common.merkle.route.internal.BinaryMerkleRoute;
import com.swirlds.common.merkle.route.internal.UncompressedMerkleRoute;
import java.util.List;

/**
 * A factory for new merkle routes.
 */
public final class MerkleRouteFactory {

    private static MerkleRoute emptyRoute = new BinaryMerkleRoute();

    private MerkleRouteFactory() {}

    /**
     * Various encoding strategies for merkle routes.
     */
    public enum MerkleRouteEncoding {
        /**
         * Routes are compressed. Optimized heavily for routes with sequences of binary steps.
         */
        BINARY_COMPRESSION,
        /**
         * Routes are completely uncompressed. Uses more memory but is faster to read and write.
         */
        UNCOMPRESSED
    }

    /**
     * Specify the algorithm for encoding merkle routes.
     */
    public static void setRouteEncodingStrategy(final MerkleRouteEncoding encoding) {
        switch (encoding) {
            case BINARY_COMPRESSION:
                emptyRoute = new BinaryMerkleRoute();
                break;
            case UNCOMPRESSED:
                emptyRoute = new UncompressedMerkleRoute();
                break;
            default:
                throw new IllegalArgumentException("Unhandled type: " + encoding);
        }
    }

    /**
     * Get an empty merkle route.
     */
    public static MerkleRoute getEmptyRoute() {
        return emptyRoute;
    }

    /**
     * Build a route out of a sequence of steps.
     */
    public static MerkleRoute buildRoute(final List<Integer> steps) {
        return getEmptyRoute().extendRoute(steps);
    }

    /**
     * Build a route out of a sequence of steps.
     */
    public static MerkleRoute buildRoute(final int... steps) {
        return getEmptyRoute().extendRoute(steps);
    }
}
