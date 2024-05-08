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

package com.swirlds.platform.tss.keying;

import com.swirlds.platform.tss.bls.BlsPrivateKey;
import com.swirlds.platform.tss.ecdh.EcdhPrivateKey;
import com.swirlds.platform.tss.ecdh.EcdhPublicKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

public interface TssRekeying {

    /**
     * Build a distributed key share for genesis. Once enough distributed keys have been collected, they can be combined
     * to discover public BLS keys and our private keys.
     *
     * @param publicKeys  the public keys of the nodes
     * @param keyMap      TODO
     * @param seed        the seed to use, should be chosen using a CSPRNG
     * @param threshold   the number of shares out of the total that are required to form a complete signature
     * @param totalShares the total number of shares that will be generated
     * @return the distributed key
     */
    @NonNull
    DistributedKeyShare buildGenesisDistributedKeyShare( // TODO no private key here?
            @NonNull List<EcdhPublicKey> publicKeys, @NonNull KeyMap keyMap, long seed, int threshold, int totalShares);

    /**
     * Build a distributed key share for a re-keying operation. Once enough distributed keys have been collected, they
     * can be combined to discover public BLS keys and our private keys.
     *
     * @param publicKeys    the public keys of the nodes
     * @param keyMap        TODO
     * @param myPrivateKeys the private keys of the node
     * @param threshold     the number of shares out of the total that are required to form a complete signature
     * @param totalShares   the total number of shares that will be generated
     * @return the distributed key
     */
    @NonNull
    DistributedKeyShare buildDistributedKeyShare(
            @NonNull List<EcdhPublicKey> publicKeys,
            @NonNull KeyMap keyMap,
            @NonNull List<BlsPrivateKey> myPrivateKeys,
            int threshold,
            int totalShares);

    /**
     * Combine distributed key shares to discover public BLS keys and our private keys.
     *
     * @param myPrivateKey         the private key of the node
     * @param distributedKeyShares the distributed key shares
     * @param keyMap               TODO
     * @return the public keys and private keys
     */
    TssResults combineDistributedKeyShares(
            @NonNull EcdhPrivateKey myPrivateKey,
            @NonNull List<DistributedKeyShare> distributedKeyShares,
            @NonNull KeyMap keyMap);
}
