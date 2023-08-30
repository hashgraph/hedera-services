package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.util.Objects;

/**
 * Gives easier access to some basic facts about a call received by the
 * {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract},
 * including the type of call, the selector, and whether it's a token redirect.
 */
public class LegibleHtsCall {
    private final Bytes input;

    public enum Type {
        TRANSFER,
        FUNGIBLE_MINT,
        NON_FUNGIBLE_MINT,
        ASSOCIATE_ONE,
        ASSOCIATE_MANY,
        DISSOCIATE_ONE,
        DISSOCIATE_MANY,
        PAUSE_TOKEN,
        UNPAUSE_TOKEN,
        FREEZE_ACCOUNT,
        UNFREEZE_ACCOUNT,
        GRANT_KYC,
        REVOKE_KYC,
        WIPE_AMOUNT,
        WIPE_SERIAL_NUMBERS,
        GRANT_ALLOWANCE,
        GRANT_APPROVAL,
        APPROVE_OPERATOR,
        CREATE_TOKEN,
        DELETE_TOKEN,
        UPDATE_TOKEN,
        GET_ALLOWANCE,
        GET_IS_APPROVED,
        GET_IS_OPERATOR,
        GET_IS_KYC,
        GET_NFT_INFO,
        GET_TOKEN_INFO,
    }

    public LegibleHtsCall(@NonNull final Bytes input) {
        this.input = Objects.requireNonNull(input);
    }

    public Type type() {
        throw new AssertionError("Not implemented");
    }

    public int selector() {
        throw new AssertionError("Not implemented");
    }

    public boolean isTokenRedirect() {
        throw new AssertionError("Not implemented");
    }

    public Address redirectTokenAddress() {
        throw new AssertionError("Not implemented");
    }

    public int redirectSelector() {
        throw new AssertionError("Not implemented");
    }
}
