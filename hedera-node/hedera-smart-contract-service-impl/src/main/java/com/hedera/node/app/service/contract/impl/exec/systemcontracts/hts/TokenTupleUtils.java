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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Utility class with methods for assembling {@code Tuple} containing token information
 */
public class TokenTupleUtils {
    private TokenTupleUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns a tuple of the following {@Code Expiry} struct
     * {@Code struct Expiry { unint32 second; address autoRenewAccount; uint32 autoRenewPeriod; } }
     * @param token
     * @return Tuple encoding of the Expiry
     */
    @NonNull
    public static Tuple expiryTupleFor(@NonNull final Token token) {
        return Tuple.of(
                token.expirationSecond(),
                headlongAddressOf(token.autoRenewAccountIdOrElse(ZERO_ACCOUNT_ID)),
                token.autoRenewSeconds());
    }

    /**
     * Returns a tuple of the following {@Code KeyValue} struct
     * struct KeyValue { bool inheritAccountKey; address contractId; bytes ed25519; bytes ECDSA_secp256k1; address delegatableContractId; }
     * @param key
     * @return Tuple encoding of the KeyValue
     */
    @NonNull
    public static Tuple keyTupleFor(@NonNull final Key key) {
        return Tuple.of(
                false,
                headlongAddressOf(key.contractIDOrElse(ZERO_CONTRACT_ID)),
                key.ed25519OrElse(Bytes.EMPTY).toByteArray(),
                key.ecdsaSecp256k1OrElse(Bytes.EMPTY).toByteArray(),
                headlongAddressOf(key.delegatableContractIdOrElse(ZERO_CONTRACT_ID)));
    }
}
