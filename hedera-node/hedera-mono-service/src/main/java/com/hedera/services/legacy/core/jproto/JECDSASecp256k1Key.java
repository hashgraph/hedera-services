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
package com.hedera.services.legacy.core.jproto;

import com.swirlds.common.utility.CommonUtils;
import java.util.Arrays;

/** Maps to proto Key of type ECDSA_secp256k1Key */
public class JECDSASecp256k1Key extends JKey {
    private byte[] ecdsaSecp256k1Key;

    public static final int ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH = 33;

    public JECDSASecp256k1Key(final byte[] ecdsaSecp256k1Key) {
        this.ecdsaSecp256k1Key = ecdsaSecp256k1Key;
    }

    @Override
    public boolean isEmpty() {
        return ((null == ecdsaSecp256k1Key) || (0 == ecdsaSecp256k1Key.length));
    }

    @Override
    public boolean isValid() {
        return !(isEmpty()
                || (ecdsaSecp256k1Key.length != ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH)
                || (ecdsaSecp256k1Key[0] != 0x02 && ecdsaSecp256k1Key[0] != 0x03));
    }

    @Override
    public byte[] getECDSASecp256k1Key() {
        return ecdsaSecp256k1Key;
    }

    @Override
    public String toString() {
        return "<JECDSASecp256k1Key: ecdsaSecp256k1Key hex="
                + CommonUtils.hex(ecdsaSecp256k1Key)
                + ">";
    }

    @Override
    public boolean hasECDSAsecp256k1Key() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || JECDSASecp256k1Key.class != o.getClass()) {
            return false;
        }
        final var that = (JECDSASecp256k1Key) o;
        return Arrays.equals(this.ecdsaSecp256k1Key, that.ecdsaSecp256k1Key);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ecdsaSecp256k1Key);
    }
}
