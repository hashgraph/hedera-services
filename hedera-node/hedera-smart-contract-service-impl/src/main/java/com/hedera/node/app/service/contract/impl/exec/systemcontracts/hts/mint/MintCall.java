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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import org.hyperledger.besu.datatypes.Address;

public interface MintCall extends HtsCall {
    Function MINT = new Function("mintToken(address,uint64,bytes[])", ReturnTypes.INT);
    Function MINT_V2 = new Function("mintToken(address,int64,bytes[])", ReturnTypes.INT);

    static boolean matches(@NonNull final byte[] selector) {
        requireNonNull(selector);
        return Arrays.equals(selector, MINT.selector()) || Arrays.equals(selector, MINT_V2.selector());
    }

    static @Nullable MintCall from(@NonNull final HtsCallAttempt attempt, @NonNull final Address senderAddress) {
        requireNonNull(attempt);
        final var selector = attempt.selector();
        final Tuple call;
        if (Arrays.equals(selector, MINT.selector())) {
            call = MINT.decodeCall(attempt.input().toArrayUnsafe());
        } else if (Arrays.equals(selector, MINT_V2.selector())) {
            call = MINT_V2.decodeCall(attempt.input().toArrayUnsafe());
        } else {
            return null;
        }
        final var token = attempt.linkedToken(Address.fromHexString(call.get(0).toString()));
        if (token == null) {
            return null;
        } else {
            return token.tokenType() == TokenType.FUNGIBLE_COMMON
                    ? new FungibleMintCall(attempt.enhancement())
                    : new NonFungibleMintCall(attempt.enhancement());
        }
    }
}
