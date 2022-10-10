/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs.sourcing;

import com.google.common.base.MoreObjects;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.swirlds.common.utility.CommonUtils;
import java.util.Arrays;

/**
 * A source of cryptographic signatures backed by a {@link SignatureMap} instance.
 *
 * <p><b>IMPORTANT:</b> If a public key does not match any prefix in the backing {@code
 * SignatureMap}, we simply return an empty {@code byte[]} for its cryptographic signature. It might
 * seem that we should instead fail fast (since an empty signature can never be {@code VALID}).
 *
 * <p>However, this would be a mistake, since with e.g. Hedera threshold keys it is quite possible
 * for a Hedera key to be active even if some number of its constituent simple keys lack a valid
 * signature.
 */
public class PojoSigMapPubKeyToSigBytes implements PubKeyToSigBytes {
    private static final int MISSING_SIG_BYTES_INDEX = -1;

    private final PojoSigMap pojoSigMap;
    private final boolean[] used;

    public PojoSigMapPubKeyToSigBytes(SignatureMap sigMap) {
        pojoSigMap = PojoSigMap.fromGrpc(sigMap);
        used = new boolean[pojoSigMap.numSigsPairs()];
    }

    @Override
    public byte[] sigBytesFor(byte[] pubKey) throws KeyPrefixMismatchException {
        var chosenSigBytesIndex = MISSING_SIG_BYTES_INDEX;
        byte[] sigBytes = EMPTY_SIG;
        for (int i = 0, n = pojoSigMap.numSigsPairs(); i < n; i++) {
            final byte[] pubKeyPrefix = pojoSigMap.pubKeyPrefix(i);
            if (beginsWith(pubKey, pubKeyPrefix)) {
                if (sigBytes != EMPTY_SIG) {
                    throw new KeyPrefixMismatchException(
                            "Source signature map with prefix "
                                    + CommonUtils.hex(pubKeyPrefix)
                                    + " is ambiguous for given public key! ("
                                    + CommonUtils.hex(pubKey)
                                    + ")");
                }
                sigBytes = pojoSigMap.primitiveSignature(i);
                chosenSigBytesIndex = i;
            }
        }
        if (chosenSigBytesIndex != MISSING_SIG_BYTES_INDEX) {
            used[chosenSigBytesIndex] = true;
        }
        return sigBytes;
    }

    @Override
    public void forEachUnusedSigWithFullPrefix(final SigObserver observer) {
        for (int i = 0, n = pojoSigMap.numSigsPairs(); i < n; i++) {
            if (!used[i] && pojoSigMap.isFullPrefixAt(i)) {
                observer.accept(
                        pojoSigMap.keyType(i),
                        pojoSigMap.pubKeyPrefix(i),
                        pojoSigMap.primitiveSignature(i));
            }
        }
    }

    @Override
    public boolean hasAtLeastOneUnusedSigWithFullPrefix() {
        for (int i = 0, n = pojoSigMap.numSigsPairs(); i < n; i++) {
            if (!used[i] && pojoSigMap.isFullPrefixAt(i)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void resetAllSigsToUnused() {
        for (int i = 0, n = pojoSigMap.numSigsPairs(); i < n; i++) {
            used[i] = false;
        }
    }

    public static boolean beginsWith(byte[] pubKey, byte[] prefix) {
        if (pubKey.length < prefix.length) {
            return false;
        }
        int n = prefix.length;
        return Arrays.equals(prefix, 0, n, pubKey, 0, n);
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("pojoSigMap", pojoSigMap.toString())
                .add("used", used)
                .toString();
    }
}
