/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.codec;

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public final class KeyValueWrapper {
    public enum KeyValueType {
        INVALID_KEY,
        INHERIT_ACCOUNT_KEY,
        CONTRACT_ID,
        DELEGATABLE_CONTRACT_ID,
        ED25519,
        ECDSA_SECPK256K1
    }

    /* ---  Only 1 of these values should be set when the input is valid. --- */
    private final boolean shouldInheritAccountKey;
    private final ContractID contractID;
    private final byte[] ed25519;
    private final byte[] ecdsaSecp256k1;
    private final ContractID delegatableContractID;
    private final KeyValueWrapper.KeyValueType keyValueType;

    /* --- This field is populated only when `shouldInheritAccountKey` is true --- */
    private Key inheritedKey;

    public KeyValueWrapper(
            final boolean shouldInheritAccountKey,
            final ContractID contractID,
            final byte[] ed25519,
            final byte[] ecdsaSecp256k1,
            final ContractID delegatableContractID) {
        var isKeyValid =
                keyValidityCheck(
                        shouldInheritAccountKey,
                        contractID,
                        ed25519,
                        ecdsaSecp256k1,
                        delegatableContractID);
        this.shouldInheritAccountKey = shouldInheritAccountKey;
        this.contractID = contractID;
        this.ed25519 = ed25519;
        this.ecdsaSecp256k1 = ecdsaSecp256k1;
        this.delegatableContractID = delegatableContractID;
        this.keyValueType = isKeyValid ? this.setKeyValueType() : KeyValueType.INVALID_KEY;
    }

    private boolean isContractIDSet() {
        return contractID != null;
    }

    private boolean isDelegatableContractIdSet() {
        return delegatableContractID != null;
    }

    public boolean isShouldInheritAccountKeySet() {
        return shouldInheritAccountKey;
    }

    private boolean isEd25519KeySet() {
        return ed25519.length == JEd25519Key.ED25519_BYTE_LENGTH;
    }

    private boolean isEcdsaSecp256k1KeySet() {
        return ecdsaSecp256k1.length == JECDSASecp256k1Key.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH;
    }

    public void setInheritedKey(final Key key) {
        this.inheritedKey = key;
    }

    private boolean keyValidityCheck(
            boolean shouldInheritAccountKey,
            ContractID contractID,
            byte[] ed25519,
            byte[] ecdsaSecp256k1,
            ContractID delegatableContractID) {
        var keyCount = 0;
        if (contractID != null) keyCount++;
        if (delegatableContractID != null) keyCount++;
        if (shouldInheritAccountKey) keyCount++;
        if (ed25519.length == JEd25519Key.ED25519_BYTE_LENGTH) keyCount++;
        if (ecdsaSecp256k1.length == JECDSASecp256k1Key.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH)
            keyCount++;
        return keyCount == 1;
    }

    private KeyValueType setKeyValueType() {
        if (isShouldInheritAccountKeySet()) {
            return (!isEcdsaSecp256k1KeySet()
                            && !isDelegatableContractIdSet()
                            && !isContractIDSet()
                            && !isEd25519KeySet())
                    ? KeyValueType.INHERIT_ACCOUNT_KEY
                    : KeyValueType.INVALID_KEY;
        } else if (isContractIDSet()) {
            return !isEcdsaSecp256k1KeySet() && !isDelegatableContractIdSet() && !isEd25519KeySet()
                    ? KeyValueType.CONTRACT_ID
                    : KeyValueType.INVALID_KEY;
        } else if (isEd25519KeySet()) {
            return !isEcdsaSecp256k1KeySet() && !isDelegatableContractIdSet()
                    ? KeyValueType.ED25519
                    : KeyValueType.INVALID_KEY;
        } else if (isEcdsaSecp256k1KeySet()) {
            return !isDelegatableContractIdSet()
                    ? KeyValueType.ECDSA_SECPK256K1
                    : KeyValueType.INVALID_KEY;
        } else {
            return isDelegatableContractIdSet()
                    ? KeyValueType.DELEGATABLE_CONTRACT_ID
                    : KeyValueType.INVALID_KEY;
        }
    }

    public KeyValueType getKeyValueType() {
        return this.keyValueType;
    }

    public ContractID getContractID() {
        return this.contractID;
    }

    public ContractID getDelegatableContractID() {
        return this.delegatableContractID;
    }

    public byte[] getEd25519Key() {
        return this.ed25519;
    }

    public byte[] getEcdsaSecp256k1() {
        return this.ecdsaSecp256k1;
    }

    public Key asGrpc() {
        return switch (keyValueType) {
            case INHERIT_ACCOUNT_KEY -> this.inheritedKey;
            case CONTRACT_ID -> Key.newBuilder().setContractID(contractID).build();
            case ED25519 -> Key.newBuilder().setEd25519(ByteString.copyFrom(ed25519)).build();
            case ECDSA_SECPK256K1 -> Key.newBuilder()
                    .setECDSASecp256K1(ByteString.copyFrom(ecdsaSecp256k1))
                    .build();
            case DELEGATABLE_CONTRACT_ID -> Key.newBuilder()
                    .setDelegatableContractId(delegatableContractID)
                    .build();
            default -> throw new InvalidTransactionException(ResponseCodeEnum.FAIL_INVALID);
        };
    }
}
