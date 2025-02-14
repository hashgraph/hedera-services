// SPDX-License-Identifier: Apache-2.0
package com.swirlds.merkle.test.fixtures.map.pta;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import java.io.IOException;

public interface MapValue extends MerkleNode {

    /**
     * calculates Hash for MapValue based on entity type
     *
     * @return Hash
     */
    Hash calculateHash() throws IOException;

    default long getUid() {
        return 0;
    }
}
