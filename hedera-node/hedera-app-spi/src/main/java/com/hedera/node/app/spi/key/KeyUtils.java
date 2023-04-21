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

package com.hedera.node.app.spi.key;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Key.KeyOneOfType;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Utility class for working with keys. This validates if the key is empty and valid.
 */
// NOTE: This class is not in the right place. But is needed by several modules.
// !!!!!!!!!!🔥🔥🔥 It should be moved once we find where to keep it. 🔥🔥🔥!!!!!!!!!!!
public class KeyUtils {
    public static final int ED25519_BYTE_LENGTH = 32;
    private static final byte ODD_PARITY = (byte) 0x03;
    private static final byte EVEN_PARITY = (byte) 0x02;
    public static final int ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH = 33;
    /**
     * Checks if the given key is empty.
     * For a KeyList type checks if the list is empty.
     * For a ThresholdKey type checks if the list is empty.
     * For an Ed25519 or EcdsaSecp256k1 type checks if there are zero bytes.
     * TODO: This method need to be updated for other key types.
     * @param pbjKey the key to check
     * @return true if the key is empty, false otherwise
     */
    public static boolean isEmpty(@Nullable final Key pbjKey) {
        if (pbjKey == null) {
            return true;
        }
        final var key = pbjKey.key();
        if (key == null || KeyOneOfType.UNSET.equals(key.kind())) {
            return true;
        }
        if (pbjKey.hasKeyList()) {
            return !((KeyList) key.value()).hasKeys()
                    || (((KeyList) key.value()).hasKeys()
                            && ((KeyList) key.value()).keys().isEmpty());
        } else if (pbjKey.hasThresholdKey()) {
            return !((ThresholdKey) key.value()).hasKeys()
                    || (((ThresholdKey) key.value()).hasKeys()
                            && ((ThresholdKey) key.value()).keys().keys().isEmpty());
        } else if (pbjKey.hasEd25519()) {
            return ((Bytes) key.value()).length() == 0;
        } else if (pbjKey.hasEcdsaSecp256k1()) {
            return ((Bytes) key.value()).length() == 0;
        } else if (pbjKey.hasDelegatableContractId()) {
            return !((ContractID) key.value()).hasContractNum()
                    || (((ContractID) key.value()).hasContractNum() && ((ContractID) key.value()).contractNum() == 0);
        } else if (pbjKey.hasContractID()) {
            return !((ContractID) key.value()).hasContractNum()
                    || (((ContractID) key.value()).hasContractNum() && ((ContractID) key.value()).contractNum() == 0);
        }
        // ECDSA_384 and RSA_3072 are not supported yet
        return true;
    }

    /**
     * Checks if the gReaiven key is valid. Based on the key type it checks the basic requirements
     * for the key type.
     * @param pbjKey the key to check
     * @return true if the key is valid, false otherwise
     */
    public static boolean isValid(@Nullable final Key pbjKey) {
        if (isEmpty(pbjKey)) {
            return false;
        }
        final var key = pbjKey.key();
        if (pbjKey.hasKeyList()) {
            for (Key keys : ((KeyList) key.value()).keys()) {
                if (!isValid(keys)) {
                    return false;
                }
            }
            return true;
        } else if (pbjKey.hasThresholdKey()) {
            final int length = ((ThresholdKey) key.value()).keys().keys().size();
            final int threshold = ((ThresholdKey) key.value()).threshold();
            boolean isKeyListValid = true;
            for (Key keys : ((ThresholdKey) key.value()).keys().keys()) {
                if (!isValid(keys)) {
                    isKeyListValid = false;
                    break;
                }
            }
            return (threshold >= 1 && threshold <= length && isKeyListValid);
        } else if (pbjKey.hasEd25519()) {
            return ((Bytes) key.value()).length() == ED25519_BYTE_LENGTH;
        } else if (pbjKey.hasEcdsaSecp256k1()) {
            final var ecKey = ((Bytes) key.value());
            return ecKey.length() == ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH
                    && ((ecKey.getByte(0) == EVEN_PARITY || ecKey.getByte(0) == ODD_PARITY));
        } else if (pbjKey.hasDelegatableContractId()) {
            return ((ContractID) key.value()).contractNum().intValue() > 0;
        } else if (pbjKey.hasContractID()) {
            return ((ContractID) key.value()).contractNum().intValue() > 0;
        }
        // ECDSA_384 and RSA_3072 are not supported yet
        return true;
    }
}
