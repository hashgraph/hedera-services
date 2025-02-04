/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.CallTranslator;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethod;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethod.SystemContract;
import com.hedera.node.app.service.contract.impl.exec.utils.SystemContractMethodRegistry;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * Manages the call attempted by a {@link Bytes} payload received by the {@link HtsSystemContract}.
 * Translates a valid attempt into an appropriate {@link Call} subclass, giving the {@link Call}
 * everything it will need to execute.
 */
public class HtsCallAttempt extends AbstractCallAttempt<HtsCallAttempt> {
    /** Selector for redirectForToken(address,bytes) method. */
    public static final Function REDIRECT_FOR_TOKEN = new Function("redirectForToken(address,bytes)");

    // The id address of the account authorizing the call, in the sense
    // that (1) a dispatch should omit the key of this account from the
    // set of required signing keys; and (2) the verification strategy
    // for this call should use this authorizing address. We only need
    // this because we will still have two contracts on the qualified
    // delegates list, so it is possible the authorizing account can be
    // different from the EVM sender address
    private final AccountID authorizingId;

    @Nullable
    private final Token redirectToken;

    // too many parameters
    @SuppressWarnings("java:S107")
    public HtsCallAttempt(
            @NonNull final ContractID contractID,
            @NonNull final Bytes input,
            @NonNull final Address senderAddress,
            @NonNull final Address authorizingAddress,
            final boolean onlyDelegatableContractKeysActive,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final Configuration configuration,
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final VerificationStrategies verificationStrategies,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final List<CallTranslator<HtsCallAttempt>> callTranslators,
            @NonNull final SystemContractMethodRegistry systemContractMethodRegistry,
            final boolean isStaticCall) {
        super(
                contractID,
                input,
                senderAddress,
                authorizingAddress,
                onlyDelegatableContractKeysActive,
                enhancement,
                configuration,
                addressIdConverter,
                verificationStrategies,
                gasCalculator,
                callTranslators,
                isStaticCall,
                systemContractMethodRegistry,
                REDIRECT_FOR_TOKEN);
        if (isRedirect()) {
            this.redirectToken = linkedToken(redirectAddress);
        } else {
            redirectToken = null;
        }
        this.authorizingId =
                (authorizingAddress != senderAddress) ? addressIdConverter.convertSender(authorizingAddress) : senderId;
    }

    @Override
    protected SystemContract systemContractKind() {
        return SystemContractMethod.SystemContract.HTS;
    }

    @Override
    protected HtsCallAttempt self() {
        return this;
    }

    /**
     * Returns whether this is a token redirect.
     *
     * @return whether this is a token redirect
     * @throws IllegalStateException if this is not a valid call
     */
    public boolean isTokenRedirect() {
        return isRedirect();
    }

    /**
     * Returns the token that is the target of this redirect, if it existed.
     *
     * @return the token that is the target of this redirect, or null if it didn't exist
     * @throws IllegalStateException if this is not a token redirect
     */
    public @Nullable Token redirectToken() {
        if (!isRedirect()) {
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
        if (!isRedirect()) {
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
        if (!isRedirect()) {
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
        final var shard = enhancement.nativeOperations().getShardNum();
        final var realm = enhancement.nativeOperations().getRealmNum();
        if (isLongZeroAddress(shard, realm, evmAddress)) {
            return enhancement.nativeOperations().getToken(numberOfLongZero(evmAddress));
        } else {
            // No point in looking up a token that can't exist
            return null;
        }
    }

    /**
     * Returns the ID of the sender of this call in the EVM frame.
     *
     * @return the ID of the sender of this call in the EVM frame
     */
    public AccountID authorizingId() {
        return authorizingId;
    }
}
