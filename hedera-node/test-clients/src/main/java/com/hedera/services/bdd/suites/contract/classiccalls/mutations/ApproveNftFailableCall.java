// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.suites.contract.classiccalls.mutations;

import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_ACCOUNT_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_NFT_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_TOKEN_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.ALICE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.INVALID_ACCOUNT_ADDRESS;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.INVALID_TOKEN_ADDRESS;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.VALID_NON_FUNGIBLE_TOKEN_IDS;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.contract.classiccalls.AbstractFailableNonStaticCall;
import com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.EnumSet;

public class ApproveNftFailableCall extends AbstractFailableNonStaticCall {
    private static final Function SIGNATURE = new Function("approveNFT(address,address,uint256)", "(int64)");

    public ApproveNftFailableCall() {
        super(EnumSet.of(INVALID_TOKEN_ID_FAILURE, INVALID_ACCOUNT_ID_FAILURE, INVALID_NFT_ID_FAILURE));
    }

    @Override
    public String name() {
        return "approveNFT";
    }

    @Override
    public byte[] encodedCall(@NonNull final ClassicFailureMode mode, @NonNull final HapiSpec spec) {
        throwIfUnsupported(mode);
        final var validAccountAddress = idAsHeadlongAddress(spec.registry().getAccountID(ALICE));
        final var validTokenAddress = idAsHeadlongAddress(spec.registry().getTokenID(VALID_NON_FUNGIBLE_TOKEN_IDS[0]));
        if (mode == INVALID_TOKEN_ID_FAILURE) {
            return SIGNATURE
                    .encodeCallWithArgs(INVALID_TOKEN_ADDRESS, validAccountAddress, BigInteger.ONE)
                    .array();
        } else if (mode == INVALID_NFT_ID_FAILURE) {
            return SIGNATURE
                    .encodeCallWithArgs(validTokenAddress, validAccountAddress, BigInteger.valueOf(Long.MAX_VALUE))
                    .array();
        } else {
            // Must be INVALID_ACCOUNT_ID_FAILURE
            return SIGNATURE
                    .encodeCallWithArgs(validTokenAddress, INVALID_ACCOUNT_ADDRESS, BigInteger.ONE)
                    .array();
        }
    }
}
