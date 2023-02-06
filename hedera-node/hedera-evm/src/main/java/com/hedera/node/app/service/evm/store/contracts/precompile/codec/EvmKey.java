/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

    private Address contractId;

    private byte[] ed25519;

    private byte[] ecdsaSecp256K1;

    private Address delegatableContractId;

    public EvmKey() {
        this.ed25519 = new byte[0];
        this.ecdsaSecp256K1 = new byte[0];
    }

    public EvmKey(
            Address contractId,
            byte[] ed25519,
            byte[] ecdsaSecp256K1,
            Address delegatableContractId) {
        this.contractId = contractId;
        this.ed25519 = ed25519;
        this.ecdsaSecp256K1 = ecdsaSecp256K1;
        this.delegatableContractId = delegatableContractId;
    }

    public Address getContractId() {
        return contractId != null ? contractId : Address.ZERO;
    }

    public byte[] getEd25519() {
        return ed25519;
    }

    public byte[] getECDSASecp256K1() {
        return ecdsaSecp256K1;
    }

    public Address getDelegatableContractId() {
        return delegatableContractId != null ? delegatableContractId : Address.ZERO;
    }
}
