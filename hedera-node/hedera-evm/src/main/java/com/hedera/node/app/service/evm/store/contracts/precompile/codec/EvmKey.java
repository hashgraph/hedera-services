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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import org.hyperledger.besu.datatypes.Address;

public class EvmKey {

    private final Address contractId;

    private final byte[] ed25519;

    private final byte[] ECDSA_secp256k1;

    private final Address delegatableContractId;

    public EvmKey(
            Address contractId,
            byte[] ed25519,
            byte[] ECDSA_secp256k1,
            Address delegatableContractId) {
        this.contractId = contractId;
        this.ed25519 = ed25519;
        this.ECDSA_secp256k1 = ECDSA_secp256k1;
        this.delegatableContractId = delegatableContractId;
    }

    public Address getContractId() {
        return contractId;
    }

    public byte[] getEd25519() {
        return ed25519;
    }

    public byte[] getECDSA_secp256k1() {
        return ECDSA_secp256k1;
    }

    public Address getDelegatableContractId() {
        return delegatableContractId;
    }
}
