/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.BufferUnderflowException;
import java.util.Arrays;
import java.util.List;
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

    private final byte[] selector;
    private final Bytes input;
    private final boolean isRedirect;

    // The id address of the account authorizing the call, in the sense
    // that (1) a dispatch should omit the key of this account from the
    // set of required signing keys; and (2) the verification strategy
    // for this call should use this authorizing address. We only need
    // this because we will still have two contracts on the qualified
    // delegates list, so it is possible the authorizing account can be
    // different from the EVM sender address
    private final AccountID authorizingId;
    private final Address authorizingAddress;
    // The id of the sender in the EVM frame
    private final AccountID senderId;
    private final Address senderAddress;
    private final boolean onlyDelegatableContractKeysActive;

    @Nullable
    private final Token redirectToken;

    private final HederaWorldUpdater.Enhancement enhancement;
    private final Configuration configuration;
    private final AddressIdConverter addressIdConverter;
    private final VerificationStrategies verificationStrategies;
    private final SystemContractGasCalculator gasCalculator;
    private final List<HtsCallTranslator> callTranslators;
    private final boolean isStaticCall;
    // too many parameters
    @SuppressWarnings("java:S107")
    public HtsCallAttempt(
            @NonNull final Bytes input,
            @NonNull final Address senderAddress,
            @NonNull final Address authorizingAddress,
            final boolean onlyDelegatableContractKeysActive,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final Configuration configuration,
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final VerificationStrategies verificationStrategies,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final List<HtsCallTranslator> callTranslators,
            final boolean isStaticCall) {
        requireNonNull(input);
        this.callTranslators = requireNonNull(callTranslators);
        this.gasCalculator = requireNonNull(gasCalculator);
        this.senderAddress = requireNonNull(senderAddress);
        this.authorizingAddress = requireNonNull(authorizingAddress);
        this.configuration = requireNonNull(configuration);
        this.addressIdConverter = requireNonNull(addressIdConverter);
        this.enhancement = requireNonNull(enhancement);
        this.verificationStrategies = requireNonNull(verificationStrategies);
        this.onlyDelegatableContractKeysActive = onlyDelegatableContractKeysActive;

        this.isRedirect = isRedirect(input.toArrayUnsafe());
        if (this.isRedirect) {
            Tuple abiCall = null;
            try {
                // First try to decode the redirect with standard ABI encoding using a 32-byte address
                abiCall = REDIRECT_FOR_TOKEN.decodeCall(input.toArrayUnsafe());
            } catch (IllegalArgumentException | BufferUnderflowException | IndexOutOfBoundsException ignore) {
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
        this.senderId = addressIdConverter.convertSender(senderAddress);
        this.authorizingId =
                (authorizingAddress != senderAddress) ? addressIdConverter.convertSender(authorizingAddress) : senderId;
        this.isStaticCall = isStaticCall;
    }

    /**
     * Returns the default verification strategy for this call (i.e., the strategy that treats only
     * contract id and delegatable contract id keys as active when they match the call's sender address).
     *
     * @return the default verification strategy for this call
     */
    public @NonNull VerificationStrategy defaultVerificationStrategy() {
        return verificationStrategies.activatingOnlyContractKeysFor(
                authorizingAddress, onlyDelegatableContractKeysActive, enhancement.nativeOperations());
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
     * Returns the system contract gas calculator for this call.
     *
     * @return the system contract gas calculator for this call
     */
    public @NonNull SystemContractGasCalculator systemContractGasCalculator() {
        return gasCalculator;
    }

    /**
     * Returns the native operations this call was attempted within.
     *
     * @return the native operations this call was attempted within
     */
    public @NonNull HederaNativeOperations nativeOperations() {
        return enhancement.nativeOperations();
    }

    /**
     * Tries to translate this call attempt into a {@link HtsCall} from the given sender address.
     *
     * @return the executable call, or null if this attempt can't be translated to one
     */
    public @Nullable HtsCall asExecutableCall() {
        for (final var translator : callTranslators) {
            final var call = translator.translateCallAttempt(this);
            if (call != null) {
                return call;
            }
        }
        return null;
    }

    /**
     * Returns the ID of the sender of this call.
     *
     * @return the ID of the sender of this call
     */
    public @NonNull AccountID senderId() {
        return senderId;
    }

    /**
     * Returns the address of the sender of this call.
     *
     * @return the address of the sender of this call
     */
    public @NonNull Address senderAddress() {
        return senderAddress;
    }

    /**
     * Returns the address ID converter for this call.
     *
     * @return the address ID converter for this call
     */
    public AddressIdConverter addressIdConverter() {
        return addressIdConverter;
    }

    /**
     * Returns the configuration for this call.
     *
     * @return the configuration for this call
     */
    public Configuration configuration() {
        return configuration;
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
     * Returns the raw byte array input of this call.
     *
     * @return the raw input of this call
     * @throws IllegalStateException if this is not a valid call
     */
    public byte[] inputBytes() {
        return input.toArrayUnsafe();
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
     * Returns the id of the token that is the target of this redirect, if it existed.
     *
     * @return the id of the token that is the target of this redirect, or null if it didn't exist
     * @throws IllegalStateException if this is not a token redirect
     */
    public @Nullable TokenID redirectTokenId() {
        if (!isRedirect) {
            throw new IllegalStateException("Not a token redirect");
        }
        return redirectToken == null ? null : redirectToken.tokenId();
    }

    /**
     * Returns the type of the token that is the target of this redirect, if it existed.
     *
     * @return the type of the token that is the target of this redirect, or null if it didn't exist
     * @throws IllegalStateException if this is not a token redirect
     */
    public @Nullable TokenType redirectTokenType() {
        if (!isRedirect) {
            throw new IllegalStateException("Not a token redirect");
        }
        return redirectToken == null ? null : redirectToken.tokenType();
    }

    /**
     * Returns the token at the given Besu address, if it exists.
     *
     * @param tokenAddress the Besu address of the token to look up
     * @return the token that is the target of this redirect, or null if it didn't exist
     */
    public @Nullable Token linkedToken(@NonNull final Address tokenAddress) {
        requireNonNull(tokenAddress);
        return linkedToken(tokenAddress.toArray());
    }

    /**
     * Returns the token at the given EVM address, if it exists.
     *
     * @param evmAddress the headlong address of the token to look up
     * @return the token that is the target of this redirect, or null if it didn't exist
     */
    public @Nullable Token linkedToken(@NonNull final byte[] evmAddress) {
        requireNonNull(evmAddress);
        if (isLongZeroAddress(evmAddress)) {
            return enhancement.nativeOperations().getToken(numberOfLongZero(evmAddress));
        } else {
            // No point in looking up a token that can't exist
            return null;
        }
    }

    /**
     * @return whether the current call attempt is a static call
     */
    public boolean isStaticCall() {
        return isStaticCall;
    }

    /**
     * Returns the ID of the sender of this call in the EVM frame.
     *
     * @return the ID of the sender of this call in the EVM frame
     */
    public AccountID authorizingId() {
        return authorizingId;
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
