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
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations.AssociationsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.decimals.DecimalsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isoperator.IsApprovedForAllCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.MintCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.name.NameCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ownerof.OwnerOfCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.symbol.SymbolCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenuri.TokenUriCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.totalsupply.TotalSupplyCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc721TransferFromCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.swirlds.config.api.Configuration;
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

    private final byte[] selector;
    private final Bytes input;
    private final boolean isRedirect;

    @Nullable
    private final Token redirectToken;

    private final HederaWorldUpdater.Enhancement enhancement;
    private final Configuration configuration;
    private final DecodingStrategies decodingStrategies;
    private final AddressIdConverter addressIdConverter;
    private final VerificationStrategies verificationStrategies;

    public HtsCallAttempt(
            @NonNull final Bytes input,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final Configuration configuration,
            @NonNull final DecodingStrategies decodingStrategies,
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final VerificationStrategies verificationStrategies) {
        this.configuration = configuration;
        this.addressIdConverter = addressIdConverter;
        requireNonNull(input);
        this.isRedirect = isRedirect(input.toArrayUnsafe());
        this.enhancement = requireNonNull(enhancement);
        this.decodingStrategies = requireNonNull(decodingStrategies);
        this.verificationStrategies = requireNonNull(verificationStrategies);
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
     *
     * @param senderAddress          the address of the sender of the call
     * @param needingDelegatableKeys whether the sender needs delegatable contract keys to be authorized
     * @return the call, or null if it couldn't be translated
     */
    public @Nullable HtsCall asCallFrom(@NonNull final Address senderAddress, final boolean needingDelegatableKeys) {
        requireNonNull(senderAddress);
        if (Erc721TransferFromCall.matches(this)) {
            return Erc721TransferFromCall.from(this, senderAddress, needingDelegatableKeys);
        } else if (Erc20TransfersCall.matches(this)) {
            return Erc20TransfersCall.from(this, senderAddress, needingDelegatableKeys);
        } else if (ClassicTransfersCall.matches(this)) {
            return ClassicTransfersCall.from(this, senderAddress, needingDelegatableKeys);
        } else if (AssociationsCall.matches(this)) {
            return AssociationsCall.from(this, senderAddress, needingDelegatableKeys);
        } else if (MintCall.matches(selector)) {
            return MintCall.from(this, senderAddress);
        } else if (BalanceOfCall.matches(selector)) {
            return BalanceOfCall.from(this);
        } else if (IsApprovedForAllCall.matches(selector)) {
            return IsApprovedForAllCall.from(this);
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
     * Returns the decoding strategies for this call.
     *
     * @return the decoding strategies for this call
     */
    public DecodingStrategies decodingStrategies() {
        return decodingStrategies;
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
