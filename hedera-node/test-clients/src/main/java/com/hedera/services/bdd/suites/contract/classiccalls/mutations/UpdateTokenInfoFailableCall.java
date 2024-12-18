// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.suites.contract.classiccalls.mutations;

import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_ACCOUNT_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_TOKEN_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.ALICE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.INVALID_ACCOUNT_ADDRESS;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.INVALID_TOKEN_ADDRESS;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.VALID_FUNGIBLE_TOKEN_IDS;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.contract.classiccalls.AbstractFailableNonStaticCall;
import com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;

public class UpdateTokenInfoFailableCall extends AbstractFailableNonStaticCall {
    private static final Function SIGNATURE = new Function(
            "updateTokenInfo(address,(string,string,address,string,bool,uint32,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)))",
            "(int64)");

    public UpdateTokenInfoFailableCall() {
        super(EnumSet.of(INVALID_TOKEN_ID_FAILURE, INVALID_ACCOUNT_ID_FAILURE));
    }

    @Override
    public String name() {
        return "updateTokenInfo";
    }

    @Override
    public byte[] encodedCall(@NonNull final ClassicFailureMode mode, @NonNull final HapiSpec spec) {
        throwIfUnsupported(mode);
        final var validAccountAddress = idAsHeadlongAddress(spec.registry().getAccountID(ALICE));
        if (mode == INVALID_TOKEN_ID_FAILURE) {
            return SIGNATURE
                    .encodeCallWithArgs(
                            INVALID_TOKEN_ADDRESS,
                            Tuple.from(
                                    "Name",
                                    "SYM",
                                    validAccountAddress,
                                    "memo",
                                    false,
                                    123L,
                                    false,
                                    new Tuple[0],
                                    Tuple.of(1L, validAccountAddress, 2L)))
                    .array();
        } else {
            // Must be INVALID_ACCOUNT_ID_FAILURE
            return SIGNATURE
                    .encodeCallWithArgs(
                            idAsHeadlongAddress(spec.registry().getTokenID(VALID_FUNGIBLE_TOKEN_IDS[0])),
                            Tuple.from(
                                    "Name",
                                    "SYM",
                                    INVALID_ACCOUNT_ADDRESS,
                                    "memo",
                                    false,
                                    123L,
                                    false,
                                    new Tuple[0],
                                    Tuple.of(1L, INVALID_ACCOUNT_ADDRESS, 2L)))
                    .array();
        }
    }
}
