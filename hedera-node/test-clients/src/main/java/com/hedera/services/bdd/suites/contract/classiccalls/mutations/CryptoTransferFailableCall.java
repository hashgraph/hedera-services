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

package com.hedera.services.bdd.suites.contract.classiccalls.mutations;

import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_ACCOUNT_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_NFT_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_TOKEN_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.ALICE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.BOB;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.INVALID_ACCOUNT_ADDRESS;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.INVALID_TOKEN_ADDRESS;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.VALID_FUNGIBLE_TOKEN_IDS;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.VALID_NON_FUNGIBLE_TOKEN_IDS;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.contract.classiccalls.AbstractFailableNonStaticCall;
import com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;

public class CryptoTransferFailableCall extends AbstractFailableNonStaticCall {
    private static final Function SIGNATURE =
            new Function("cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[])");

    public CryptoTransferFailableCall() {
        super(EnumSet.of(INVALID_ACCOUNT_ID_FAILURE, INVALID_TOKEN_ID_FAILURE, INVALID_NFT_ID_FAILURE));
    }

    @Override
    public String name() {
        return "cryptoTransfer";
    }

    @Override
    public byte[] encodedCall(@NonNull final ClassicFailureMode mode, @NonNull final HapiSpec spec) {
        throwIfUnsupported(mode);
        final var aValidAccountAddress = idAsHeadlongAddress(spec.registry().getAccountID(ALICE));
        final var bValidAccountAddress = idAsHeadlongAddress(spec.registry().getAccountID(BOB));
        final var validFungibleTokenAddress =
                idAsHeadlongAddress(spec.registry().getTokenID(VALID_FUNGIBLE_TOKEN_IDS[0]));
        final var validNonFungibleTokenAddress =
                idAsHeadlongAddress(spec.registry().getTokenID(VALID_NON_FUNGIBLE_TOKEN_IDS[0]));
        if (mode == INVALID_ACCOUNT_ID_FAILURE) {
            return SIGNATURE
                    .encodeCallWithArgs(new Object[] {
                        new Tuple[] {
                            Tuple.of(
                                    validFungibleTokenAddress,
                                    new Tuple[] {
                                        Tuple.of(aValidAccountAddress, -100L), Tuple.of(INVALID_ACCOUNT_ADDRESS, 100L)
                                    },
                                    new Tuple[] {}),
                        }
                    })
                    .array();
        } else if (mode == INVALID_NFT_ID_FAILURE) {
            return SIGNATURE
                    .encodeCallWithArgs(new Object[] {
                        new Tuple[] {
                            Tuple.of(validNonFungibleTokenAddress, new Tuple[] {}, new Tuple[] {
                                Tuple.of(aValidAccountAddress, bValidAccountAddress, Long.MAX_VALUE)
                            }),
                        }
                    })
                    .array();
        } else {
            return SIGNATURE
                    .encodeCallWithArgs(new Object[] {
                        new Tuple[] {
                            Tuple.of(
                                    INVALID_TOKEN_ADDRESS,
                                    new Tuple[] {
                                        Tuple.of(aValidAccountAddress, -100L), Tuple.of(bValidAccountAddress, 100L)
                                    },
                                    new Tuple[] {}),
                        }
                    })
                    .array();
        }
    }
}
