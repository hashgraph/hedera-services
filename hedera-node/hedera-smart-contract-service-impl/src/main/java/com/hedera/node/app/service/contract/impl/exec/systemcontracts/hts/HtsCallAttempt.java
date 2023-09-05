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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isLongZero;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.decimals.DecimalsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isoperator.IsOperatorCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.MintCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.name.NameCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ownerof.OwnerOfCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.symbol.SymbolCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenuri.TokenUriCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.totalsupply.TotalSupplyCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.TransferCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * Manages the call attempted by a {@link Bytes} payload received by the {@link HtsSystemContract}.
 * Translates a valid attempt into an appropriate {@link HtsCall} subclass, giving the {@link HtsCall}
 * everything it will need to execute.
 */
public class HtsCallAttempt {
    public static final Function REDIRECT_FOR_TOKEN = new Function("redirectForToken(address,bytes)");
    private static final byte[] REDIRECT_FOR_TOKEN_SELECTOR = REDIRECT_FOR_TOKEN.selector();

    // TODO - support all of the following:
    //   [x] TRANSFER,
    //   [x] MINT,
    //   [x] BALANCE_OF,
    //   [x] TOTAL_SUPPLY,
    //   [x] DECIMALS,
    //   [ ] ASSOCIATE_ONE,
    //   [ ] ASSOCIATE_MANY,
    //   [ ] DISSOCIATE_ONE,
    //   [ ] DISSOCIATE_MANY,
    //   [ ] PAUSE_TOKEN,
    //   [ ] UNPAUSE_TOKEN,
    //   [ ] FREEZE_ACCOUNT,
    //   [ ] UNFREEZE_ACCOUNT,
    //   [ ] GRANT_KYC,
    //   [ ] REVOKE_KYC,
    //   [ ] WIPE_AMOUNT,
    //   [ ] WIPE_SERIAL_NUMBERS,
    //   [ ] GRANT_ALLOWANCE,
    //   [ ] GRANT_APPROVAL,
    //   [ ] APPROVE_OPERATOR,
    //   [ ] CREATE_TOKEN,
    //   [ ] DELETE_TOKEN,
    //   [ ] UPDATE_TOKEN,
    //   [x] GET_BALANCE,
    //   [ ] GET_ALLOWANCE,
    //   [ ] GET_IS_APPROVED,
    //   [x] GET_IS_OPERATOR,
    //   [ ] GET_IS_KYC,
    //   [ ] GET_NFT_INFO,
    //   [ ] GET_TOKEN_INFO,

    private final byte[] selector;
    private final Bytes input;
    private final boolean isRedirect;

    @Nullable
    private final Token redirectToken;

    private final HederaWorldUpdater.Enhancement enhancement;

    public HtsCallAttempt(@NonNull final Bytes input, @NonNull final HederaWorldUpdater.Enhancement enhancement) {
        requireNonNull(input);
        requireNonNull(enhancement);

        this.enhancement = enhancement;
        this.isRedirect = isRedirect(input.toArrayUnsafe());
        if (this.isRedirect) {
            Tuple abiCall = null;
            try {
                // First try to decode the redirect with standard ABI encoding using a 32-byte address
                abiCall = REDIRECT_FOR_TOKEN.decodeCall(input.toArrayUnsafe());
            } catch (Exception ignore) {
                // Otherwise use the "packed" encoding with a 20-byte address
            }
            final Address tokenAddress;
            if (abiCall != null) {
                tokenAddress = Address.fromHexString(abiCall.get(0).toString());
                this.input = Bytes.wrap((byte[]) abiCall.get(1));
            } else {
                tokenAddress = Address.wrap(input.slice(4, 20));
                this.input = input.slice(24);
            }
            this.redirectToken = linkedToken(tokenAddress);
        } else {
            redirectToken = null;
            this.input = input;
        }
        this.selector = this.input.slice(0, 4).toArrayUnsafe();
    }

    /**
     * Returns the updater enhancement this call was attempted within.
     *
     * @return the updater enhancement this call was attempted within
     */
    public @NonNull HederaWorldUpdater.Enhancement enhancement() {
        return enhancement;
    }

    /**
     * Tries to translate this call attempt into a {@link HtsCall} from the given sender address.
     *
     * @param senderAddress the address of the sender of the call
     * @return the call, or null if it couldn't be translated
     */
    public @Nullable HtsCall asCallFrom(@NonNull final Address senderAddress) {
        requireNonNull(senderAddress);
        if (TransferCall.matches(selector)) {
            return TransferCall.from(this, senderAddress);
        } else if (MintCall.matches(selector)) {
            return MintCall.from(this, senderAddress);
        } else if (BalanceOfCall.matches(selector)) {
            return BalanceOfCall.from(this);
        } else if (IsOperatorCall.matches(selector)) {
            return IsOperatorCall.from(this);
        } else if (TotalSupplyCall.matches(selector)) {
            return TotalSupplyCall.from(this);
        } else if (NameCall.matches(selector)) {
            return NameCall.from(this);
        } else if (SymbolCall.matches(selector)) {
            return SymbolCall.from(this);
        } else if (OwnerOfCall.matches(selector)) {
            return OwnerOfCall.from(this);
        } else if (TokenUriCall.matches(selector)) {
            return TokenUriCall.from(this);
        } else if (DecimalsCall.matches(selector)) {
            return DecimalsCall.from(this);
        } else {
            return null;
        }
    }

    /**
     * Returns the selector of this call.
     *
     * @return the selector of this call
     * @throws IllegalStateException if this is not a valid call
     */
    public byte[] selector() {
        return selector;
    }

    /**
     * Returns whether this is a token redirect.
     *
     * @return whether this is a token redirect
     * @throws IllegalStateException if this is not a valid call
     */
    public boolean isTokenRedirect() {
        return isRedirect;
    }

    /**
     * Returns the input of this call.
     *
     * @return the input of this call
     * @throws IllegalStateException if this is not a valid call
     */
    public Bytes input() {
        return input;
    }

    /**
     * Returns the token that is the target of this redirect, if it existed.
     *
     * @return the token that is the target of this redirect, or null if it didn't exist
     * @throws IllegalStateException if this is not a token redirect
     */
    public @Nullable Token redirectToken() {
        if (!isRedirect) {
            throw new IllegalStateException("Not a token redirect");
        }
        return redirectToken;
    }

    /**
     * Returns the token at the given address, if it exists.
     *
     * @param tokenAddress the address of the token to look up
     * @return the token that is the target of this redirect, or null if it didn't exist
     */
    public @Nullable Token linkedToken(@NonNull final Address tokenAddress) {
        requireNonNull(tokenAddress);
        if (isLongZero(tokenAddress)) {
            return enhancement.nativeOperations().getToken(numberOfLongZero(tokenAddress));
        } else {
            // No point in looking up a token that can't exist
            return null;
        }
    }

    private boolean isRedirect(final byte[] input) {
        return Arrays.equals(
                input,
                0,
                REDIRECT_FOR_TOKEN_SELECTOR.length,
                REDIRECT_FOR_TOKEN_SELECTOR,
                0,
                REDIRECT_FOR_TOKEN_SELECTOR.length);
    }
}
