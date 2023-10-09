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
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
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
    private final AccountID senderId;
    private final Address senderAddress;
    private final boolean onlyDelegatableContractKeysActive;

    @Nullable
    private final Token redirectToken;

    private final HederaWorldUpdater.Enhancement enhancement;
    private final Configuration configuration;
    private final AddressIdConverter addressIdConverter;
    private final VerificationStrategies verificationStrategies;
    private final List<HtsCallTranslator> callTranslators;
    private final boolean isStaticCall;

    public HtsCallAttempt(
            @NonNull final Bytes input,
            @NonNull final Address senderAddress,
            boolean onlyDelegatableContractKeysActive,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final Configuration configuration,
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final VerificationStrategies verificationStrategies,
            @NonNull final List<HtsCallTranslator> callTranslators,
            final boolean isStaticCall) {
        requireNonNull(input);
        this.callTranslators = requireNonNull(callTranslators);
        this.senderAddress = requireNonNull(senderAddress);
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
        this.senderId = addressIdConverter.convertSender(senderAddress);
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
                senderAddress, onlyDelegatableContractKeysActive, enhancement.nativeOperations());
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
     * Returns the native operations this call was attempted within.
     *
     * @return the native operations this call was attempted within
     */
    public @NonNull HederaNativeOperations nativeOperations() {
        return enhancement.nativeOperations();
    }

    /**
     * Tries to translate this call attempt into a {@link HtsCall} from the given sender address.
     * <p>
     * Call attempts could refer to a,
     * <ul>
     *   <li>[x] TRANSFER (ERCTransferPrecompile, TransferPrecompile)</li>
     *   <li>[ ] MINT_UNITS (MintPrecompile)</li>
     *   <li>[ ] MINT_NFTS (MintPrecompile)</li>
     *   <li>[ ] BURN_UNITS (BurnPrecompile)</li>
     *   <li>[ ] BURN_SERIAL_NOS (BurnPrecompile)</li>
     *   <li>[ ] ASSOCIATE_ONE (AssociatePrecompile) </li>
     *   <li>[ ] ASSOCIATE_MANY (MultiAssociatePrecompile)</li>
     *   <li>[ ] DISSOCIATE_ONE (DissociatePrecompile) </li>
     *   <li>[ ] DISSOCIATE_MANY (MultiDissociatePrecompile)</li>
     *   <li>[ ] PAUSE_TOKEN (PausePrecompile)</li>
     *   <li>[ ] UNPAUSE_TOKEN (UnpausePrecompile)</li>
     *   <li>[ ] FREEZE_ACCOUNT (FreezeTokenPrecompile) </li>
     *   <li>[ ] UNFREEZE_ACCOUNT (UnfreezeTokenPrecompile)</li>
     *   <li>[ ] GRANT_KYC (GrantKycPrecompile)</li>
     *   <li>[ ] REVOKE_KYC (RevokeKycPrecompile)</li>
     *   <li>[ ] WIPE_AMOUNT (WipeFungiblePrecompile)</li>
     *   <li>[ ] WIPE_SERIAL_NUMBERS (WipeNonFungiblePrecompile)</li>
     *   <li>[ ] GRANT_ALLOWANCE (ApprovePrecompile)</li>
     *   <li>[ ] GRANT_APPROVAL (ApprovePrecompile)</li>
     *   <li>[ ] APPROVE_OPERATOR (SetApprovalForAllPrecompile)</li>
     *   <li>[ ] CREATE_TOKEN (TokenCreatePrecompile)</li>
     *   <li>[ ] DELETE_TOKEN (DeleteTokenPrecompile)</li>
     *   <li>[ ] UPDATE_TOKEN</li>
     *   <li>[x] BALANCE_OF (BalanceOfPrecompile)</li>
     *   <li>[x] TOTAL_SUPPLY (TotalSupplyPrecompile)</li>
     *   <li>[x] DECIMALS (DecimalsPrecompile)</li>
     *   <li>[x] NAME (NamePrecompile)</li>
     *   <li>[x] SYMBOL (SymbolPrecompile)</li>
     *   <li>[x] OWNER_OF (OwnerOfPrecompile)</li>
     *   <li>[x] TOKEN_URI (TokenURIPrecompile</li>
     *   <li>[ ] ALLOWANCE (AllowancePrecompile)</li>
     *   <li>[ ] APPROVED (GetApprovedPrecompile)</li>
     *   <li>[ ] IS_FROZEN (IsFrozenPrecompile)</li>
     *   <li>[x] IS_APPROVED_FOR_ALL (IsApprovedForAllPrecompile)</li>
     *   <li>[ ] IS_KYC (IsKycPrecompile)</li>
     *   <li>[ ] IS_TOKEN (IsTokenPrecompile)</li>
     *   <li>[ ] NFT_INFO</li>
     *   <li>[ ] TOKEN_INFO (TokenInfoPrecompile)</li>
     *   <li>[ ] TOKEN_CUSTOM_FEES (TokenGetCustomFeesPrecompile)</li>
     *   <li>[ ] FUNGIBLE_TOKEN_INFO (FungibleTokenInfoPrecompile) </li>
     *   <li>[ ] NON_FUNGIBLE_TOKEN_INFO (NonFungibleTokenInfoPrecompile) </li>
     *   <li>[ ] TOKEN_EXPIRY_INFO (GetTokenExpiryInfoPrecompile) </li>
     *   <li>[ ] TOKEN_TYPE (GetTokenTypePrecompile) </li>
     *   <li>[ ] TOKEN_KEY (GetTokenKeyPrecompile) </li>
     *   <li>[ ] DEFAULT_FREEZE_STATUS (GetTokenDefaultFreezeStatus) </li>
     *   <li>[ ] DEFAULT_KYC_STATUS (GetTokenDefaultKycStatus) </li>
     *   <li>[ ] UPDATE_TOKEN_KEYS (TokenUpdateKeysPrecompile)</li>
     *   <li>[ ] UPDATE_TOKEN_EXPIRY (UpdateTokenExpiryInfoPrecompile)</li>
     *   <li>[ ] UPDATE_TOKEN (TokenUpdatePrecompile)</li>
     * </ul>
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
     * Returns whether only delegatable contract keys are active for this call.
     *
     * @return whether only delegatable contract keys are active for this call
     */
    public boolean onlyDelegatableContractKeysActive() {
        return onlyDelegatableContractKeysActive;
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
     * Returns the verification strategies for this call.
     *
     * @return the verification strategies for this call
     */
    public VerificationStrategies verificationStrategies() {
        return verificationStrategies;
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

    /**
     * @return whether the current call attempt is a static call
     */
    public boolean isStaticCall() {
        return isStaticCall;
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
