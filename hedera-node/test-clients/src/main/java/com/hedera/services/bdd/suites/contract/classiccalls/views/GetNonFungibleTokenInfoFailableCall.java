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

package com.hedera.services.bdd.suites.contract.classiccalls.views;

import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_NFT_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_TOKEN_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.INVALID_TOKEN_ADDRESS;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.contract.classiccalls.AbstractFailableStaticCall;
import com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode;
import com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;

public class GetNonFungibleTokenInfoFailableCall extends AbstractFailableStaticCall {
    private static final Function SIGNATURE = new Function("getNonFungibleTokenInfo(address,int64)");

    public GetNonFungibleTokenInfoFailableCall() {
        super(EnumSet.of(INVALID_TOKEN_ID_FAILURE, INVALID_NFT_ID_FAILURE));
    }

    @Override
    public String name() {
        return "getNonFungibleTokenInfo";
    }

    @Override
    public byte[] encodedCall(@NonNull final ClassicFailureMode mode, @NonNull final HapiSpec spec) {
        throwIfUnsupported(mode);
        if (mode == INVALID_TOKEN_ID_FAILURE) {
            return SIGNATURE.encodeCallWithArgs(INVALID_TOKEN_ADDRESS, 1L).array();
        } else {
            // Must be INVALID_NFT_ID_FAILURE
            return SIGNATURE
                    .encodeCallWithArgs(
                            idAsHeadlongAddress(
                                    spec.registry().getTokenID(ClassicInventory.VALID_NON_FUNGIBLE_TOKEN_IDS[0])),
                            Long.MAX_VALUE)
                    .array();
        }
    }
}
