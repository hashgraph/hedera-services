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

package com.swirlds.platform.hcm.impl.tss.groth21;

import com.swirlds.platform.hcm.api.pairings.GroupElement;
import com.swirlds.platform.hcm.api.signaturescheme.PrivateKey;
import com.swirlds.platform.hcm.api.signaturescheme.PublicKey;
import com.swirlds.platform.hcm.api.tss.TssCiphertext;
import com.swirlds.platform.hcm.api.tss.TssPrivateKey;
import com.swirlds.platform.hcm.api.tss.TssShareId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;

/**
 * A TSS ciphertext, as utilized by the Groth21 scheme.
 *
 * @param chunkRandomness  TODO
 * @param shareCiphertexts TODO
 * @param <P>              the type of public key that verifies signatures produced by the secret key encrypted by
 *                         this ciphertext
 */
public record Groth21Ciphertext<P extends PublicKey>(
        @NonNull List<GroupElement> chunkRandomness, @NonNull Map<TssShareId, List<GroupElement>> shareCiphertexts)
        implements TssCiphertext<P> {

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public TssPrivateKey<P> decryptPrivateKey(
            @NonNull final PrivateKey ecdhPrivateKey, @NonNull final TssShareId shareId) {

        final List<GroupElement> shareIdCiphertexts = shareCiphertexts.get(shareId);

        if (chunkRandomness.size() != shareIdCiphertexts.size()) {
            throw new IllegalArgumentException("Mismatched chunk randomness count and share chunk count");
        }

        //        for (j, c_j) in c.c2[receiver_index as usize].iter().enumerate() {
        //            let anti_mask_j = c.c1[j].mul(G::ScalarField::zero() - sk); // g^(-r_j  * sk)
        //            let m_j_commitment = c_j.add(anti_mask_j); // M_j = c2_j * g^(-r_j * sk) = g ^ m_j
        //            let m_j = ElGamal::<G>::brute_force_decrypt(&m_j_commitment, cache).unwrap();
        //            msg += G::ScalarField::from(256u64).pow([j as u64]) * m_j;
        //        }

        for (int i = 0; i < shareIdCiphertexts.size(); i++) {
            final GroupElement chunkCiphertext = shareIdCiphertexts.get(i);
            final GroupElement chunkRandomness = this.chunkRandomness.get(i);
        }

        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] toBytes() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
