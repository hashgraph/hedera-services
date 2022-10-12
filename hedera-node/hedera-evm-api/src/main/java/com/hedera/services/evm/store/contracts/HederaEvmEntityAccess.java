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
package com.hedera.services.evm.store.contracts;

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

public interface HederaEvmEntityAccess {
    long getBalance(Address address);

    boolean isTokenAccount(Address address);

    ByteString alias(Address address);

    boolean isExtant(Address address);

    UInt256 getStorage(Address address, UInt256 key);

    /**
     * Returns the bytecode for the contract with the given account id; or null if there is no byte
     * present for this contract.
     *
     * @param address the account's address of the target contract
     * @return the target contract's bytecode, or null if it is not present
     */
    Bytes fetchCodeIfPresent(Address address);
}
