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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateSyntheticTxnFactory.createToken;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asNumericContractId;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.utils.KeyValueWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenCreateWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenCreateWrapper.FixedFeeWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenCreateWrapper.FractionalFeeWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenCreateWrapper.RoyaltyFeeWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenExpiryWrapper;
import com.hedera.node.app.service.contract.impl.exec.utils.TokenKeyWrapper;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CreateDecoder {

    // below values correspond to  tuples' indexes
    private static final int HEDERA_TOKEN = 0;
    private static final int INIT_SUPPLY = 1;
    private static final int DECIMALS = 2;
    private static final int FIXED_FEE = 3;
    private static final int FRACTIONAL_FEE = 4;
    private static final int NFT_FIXED_FEE = 1;
    private static final int NFT_ROYALTY_FEE = 2;

    @Inject
    public CreateDecoder() {
        // Dagger2
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_FUNGIBLE_TOKEN_V1} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateFungibleTokenV1(
            @NonNull final byte[] encoded,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1.decodeCall(encoded);
        final var hederaToken = (Tuple) call.get(HEDERA_TOKEN);
        final var initSupply = ((BigInteger) call.get(INIT_SUPPLY)).longValue();
        final var decimals = ((BigInteger) call.get(DECIMALS)).intValue();
        final TokenCreateWrapper tokenCreateWrapper = getTokenCreateWrapper(
                hederaToken, true, initSupply, decimals, senderId, nativeOperations, addressIdConverter);
        return bodyOf(createToken(tokenCreateWrapper));
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_FUNGIBLE_TOKEN_V2} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateFungibleTokenV2(
            @NonNull final byte[] encoded,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_FUNGIBLE_TOKEN_V2.decodeCall(encoded);
        final var hederaToken = (Tuple) call.get(HEDERA_TOKEN);
        final var initSupply = ((BigInteger) call.get(INIT_SUPPLY)).longValue();
        final var decimals = ((Long) call.get(DECIMALS)).intValue();
        final TokenCreateWrapper tokenCreateWrapper = getTokenCreateWrapper(
                hederaToken, true, initSupply, decimals, senderId, nativeOperations, addressIdConverter);
        return bodyOf(createToken(tokenCreateWrapper));
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_FUNGIBLE_TOKEN_V3} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateFungibleTokenV3(
            @NonNull final byte[] encoded,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_FUNGIBLE_TOKEN_V3.decodeCall(encoded);
        final TokenCreateWrapper tokenCreateWrapper = getTokenCreateWrapper(
                call.get(HEDERA_TOKEN),
                true,
                call.get(INIT_SUPPLY),
                call.get(DECIMALS),
                senderId,
                nativeOperations,
                addressIdConverter);
        return bodyOf(createToken(tokenCreateWrapper));
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateFungibleTokenWithCustomFeesV1(
            @NonNull final byte[] encoded,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1.decodeCall(encoded);
        final var hederaToken = (Tuple) call.get(HEDERA_TOKEN);
        final var initSupply = ((BigInteger) call.get(INIT_SUPPLY)).longValue();
        final var decimals = ((BigInteger) call.get(DECIMALS)).intValue();
        final var fixedFee = (Tuple[]) call.get(FIXED_FEE);
        final var fractionalFees = (Tuple[]) call.get(FRACTIONAL_FEE);
        final TokenCreateWrapper tokenCreateWrapper = getTokenCreateWrapperFungibleWithCustomFees(
                hederaToken,
                initSupply,
                decimals,
                fixedFee,
                fractionalFees,
                senderId,
                nativeOperations,
                addressIdConverter);
        return bodyOf(createToken(tokenCreateWrapper));
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateFungibleTokenWithCustomFeesV2(
            @NonNull final byte[] encoded,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2.decodeCall(encoded);
        final var hederaToken = (Tuple) call.get(HEDERA_TOKEN);
        final var initSupply = ((BigInteger) call.get(INIT_SUPPLY)).longValue();
        final var decimals = ((Long) call.get(DECIMALS)).intValue();
        final var fixedFee = (Tuple[]) call.get(FIXED_FEE);
        final var fractionalFees = (Tuple[]) call.get(FRACTIONAL_FEE);
        final TokenCreateWrapper tokenCreateWrapper = getTokenCreateWrapperFungibleWithCustomFees(
                hederaToken,
                initSupply,
                decimals,
                fixedFee,
                fractionalFees,
                senderId,
                nativeOperations,
                addressIdConverter);
        return bodyOf(createToken(tokenCreateWrapper));
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateFungibleTokenWithCustomFeesV3(
            @NonNull final byte[] encoded,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3.decodeCall(encoded);
        final TokenCreateWrapper tokenCreateWrapper = getTokenCreateWrapperFungibleWithCustomFees(
                call.get(HEDERA_TOKEN),
                call.get(INIT_SUPPLY),
                call.get(DECIMALS),
                call.get(FIXED_FEE),
                call.get(FRACTIONAL_FEE),
                senderId,
                nativeOperations,
                addressIdConverter);
        return bodyOf(createToken(tokenCreateWrapper));
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V1} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateNonFungibleV1(
            @NonNull final byte[] encoded,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1.decodeCall(encoded);
        final TokenCreateWrapper tokenCreateWrapper = getTokenCreateWrapperNonFungible(
                call.get(HEDERA_TOKEN), senderId, nativeOperations, addressIdConverter);
        return bodyOf(createToken(tokenCreateWrapper));
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V2} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateNonFungibleV2(
            @NonNull final byte[] encoded,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2.decodeCall(encoded);
        final TokenCreateWrapper tokenCreateWrapper = getTokenCreateWrapperNonFungible(
                call.get(HEDERA_TOKEN), senderId, nativeOperations, addressIdConverter);
        return bodyOf(createToken(tokenCreateWrapper));
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_V3} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateNonFungibleV3(
            @NonNull final byte[] encoded,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3.decodeCall(encoded);
        final TokenCreateWrapper tokenCreateWrapper = getTokenCreateWrapperNonFungible(
                call.get(HEDERA_TOKEN), senderId, nativeOperations, addressIdConverter);
        return bodyOf(createToken(tokenCreateWrapper));
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateNonFungibleWithCustomFeesV1(
            @NonNull final byte[] encoded,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1.decodeCall(encoded);
        final TokenCreateWrapper tokenCreateWrapper = getTokenCreateWrapperNonFungibleWithCustomFees(
                call.get(HEDERA_TOKEN),
                call.get(NFT_FIXED_FEE),
                call.get(NFT_ROYALTY_FEE),
                senderId,
                nativeOperations,
                addressIdConverter);
        return bodyOf(createToken(tokenCreateWrapper));
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateNonFungibleWithCustomFeesV2(
            @NonNull final byte[] encoded,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2.decodeCall(encoded);
        final TokenCreateWrapper tokenCreateWrapper = getTokenCreateWrapperNonFungibleWithCustomFees(
                call.get(HEDERA_TOKEN),
                call.get(NFT_FIXED_FEE),
                call.get(NFT_ROYALTY_FEE),
                senderId,
                nativeOperations,
                addressIdConverter);
        return bodyOf(createToken(tokenCreateWrapper));
    }

    /**
     * Decodes a call to {@link CreateTranslator#CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCreateNonFungibleWithCustomFeesV3(
            @NonNull final byte[] encoded,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var call = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3.decodeCall(encoded);
        final TokenCreateWrapper tokenCreateWrapper = getTokenCreateWrapperNonFungibleWithCustomFees(
                call.get(HEDERA_TOKEN),
                call.get(NFT_FIXED_FEE),
                call.get(NFT_ROYALTY_FEE),
                senderId,
                nativeOperations,
                addressIdConverter);
        return bodyOf(createToken(tokenCreateWrapper));
    }

    private TransactionBody bodyOf(@NonNull final TokenCreateTransactionBody.Builder tokenCreate) {
        return TransactionBody.newBuilder().tokenCreation(tokenCreate).build();
    }

    private static TokenCreateWrapper getTokenCreateWrapper(
            @NonNull final Tuple tokenCreateStruct,
            final boolean isFungible,
            final long initSupply,
            final int decimals,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        // HederaToken
        final int TOKEN_NAME = 0;
        final int TOKEN_SYMBOL = 1;
        final int TOKEN_TREASURY = 2;
        final int MEMO = 3;
        final int SUPPLY_TYPE = 4;
        final int MAX_SUPPLY = 5;
        final int FREEZE_DEFAULT = 6;
        final int TOKEN_KEYS = 7;
        final int TOKEN_EXPIRY = 8;

        final var tokenName = (String) tokenCreateStruct.get(TOKEN_NAME);
        final var tokenSymbol = (String) tokenCreateStruct.get(TOKEN_SYMBOL);
        final var tokenTreasury = addressIdConverter.convert(tokenCreateStruct.get(TOKEN_TREASURY));
        final var memo = (String) tokenCreateStruct.get(MEMO);
        final var isSupplyTypeFinite = (Boolean) tokenCreateStruct.get(SUPPLY_TYPE);
        final var maxSupply = (long) tokenCreateStruct.get(MAX_SUPPLY);
        final var isFreezeDefault = (Boolean) tokenCreateStruct.get(FREEZE_DEFAULT);
        final var tokenKeys = decodeTokenKeys(tokenCreateStruct.get(TOKEN_KEYS), addressIdConverter);
        final var tokenExpiry = decodeTokenExpiry(tokenCreateStruct.get(TOKEN_EXPIRY), addressIdConverter);

        final var tokenCreateWrapper = new TokenCreateWrapper(
                isFungible,
                tokenName,
                tokenSymbol,
                tokenTreasury.accountNum() != 0 ? tokenTreasury : null,
                memo,
                isSupplyTypeFinite,
                initSupply,
                decimals,
                maxSupply,
                isFreezeDefault,
                tokenKeys,
                tokenExpiry);
        tokenCreateWrapper.setAllInheritedKeysTo(nativeOperations.getAccountKey(senderId.accountNum()));
        return tokenCreateWrapper;
    }

    private static TokenCreateWrapper getTokenCreateWrapperFungibleWithCustomFees(
            @NonNull final Tuple tokenCreateStruct,
            final long initSupply,
            final int decimals,
            @NonNull final Tuple[] fixedFeesTuple,
            @NonNull final Tuple[] fractionalFeesTuple,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var tokenCreateWrapper = getTokenCreateWrapper(
                tokenCreateStruct, true, initSupply, decimals, senderId, nativeOperations, addressIdConverter);
        final var fixedFees = decodeFixedFees(fixedFeesTuple, addressIdConverter);
        final var fractionalFess = decodeFractionalFees(fractionalFeesTuple, addressIdConverter);
        tokenCreateWrapper.setFixedFees(fixedFees);
        tokenCreateWrapper.setFractionalFees(fractionalFess);
        return tokenCreateWrapper;
    }

    private static TokenCreateWrapper getTokenCreateWrapperNonFungible(
            @NonNull final Tuple tokenCreateStruct,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final long initSupply = 0L;
        final int decimals = 0;
        final var tokenCreateWrapper = getTokenCreateWrapper(
                tokenCreateStruct, false, initSupply, decimals, senderId, nativeOperations, addressIdConverter);
        return tokenCreateWrapper;
    }

    private static TokenCreateWrapper getTokenCreateWrapperNonFungibleWithCustomFees(
            @NonNull final Tuple tokenCreateStruct,
            @NonNull final Tuple[] fixedFeesTuple,
            @NonNull final Tuple[] royaltyFeesTuple,
            @NonNull final AccountID senderId,
            @NonNull final HederaNativeOperations nativeOperations,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var fixedFees = decodeFixedFees(fixedFeesTuple, addressIdConverter);
        final var royaltyFees = decodeRoyaltyFees(royaltyFeesTuple, addressIdConverter);
        final long initSupply = 0L;
        final int decimals = 0;
        final var tokenCreateWrapper = getTokenCreateWrapper(
                tokenCreateStruct, false, initSupply, decimals, senderId, nativeOperations, addressIdConverter);
        tokenCreateWrapper.setFixedFees(fixedFees);
        tokenCreateWrapper.setRoyaltyFees(royaltyFees);
        return tokenCreateWrapper;
    }

    private static List<TokenKeyWrapper> decodeTokenKeys(
            @NonNull final Tuple[] tokenKeysTuples, @NonNull final AddressIdConverter addressIdConverter) {

        // TokenKey
        final int KEY_TYPE = 0;
        final int KEY_VALUE_TYPE = 1;
        // KeyValue
        final int INHERIT_ACCOUNT_KEY = 0;
        final int CONTRACT_ID = 1;
        final int ED25519 = 2;
        final int ECDSA_SECP_256K1 = 3;
        final int DELEGATABLE_CONTRACT_ID = 4;

        final List<TokenKeyWrapper> tokenKeys = new ArrayList<>(tokenKeysTuples.length);
        for (final var tokenKeyTuple : tokenKeysTuples) {
            final var keyType = ((BigInteger) tokenKeyTuple.get(KEY_TYPE)).intValue();
            final Tuple keyValueTuple = tokenKeyTuple.get(KEY_VALUE_TYPE);
            final var inheritAccountKey = (Boolean) keyValueTuple.get(INHERIT_ACCOUNT_KEY);
            final var contractId = asNumericContractId(addressIdConverter.convert(keyValueTuple.get(CONTRACT_ID)));
            final var ed25519 = (byte[]) keyValueTuple.get(ED25519);
            final var ecdsaSecp256K1 = (byte[]) keyValueTuple.get(ECDSA_SECP_256K1);
            final var delegatableContractId =
                    asNumericContractId(addressIdConverter.convert(keyValueTuple.get(DELEGATABLE_CONTRACT_ID)));

            tokenKeys.add(new TokenKeyWrapper(
                    keyType,
                    new KeyValueWrapper(
                            inheritAccountKey,
                            contractId.contractNum() != 0 ? contractId : null,
                            ed25519,
                            ecdsaSecp256K1,
                            delegatableContractId.contractNum() != 0 ? delegatableContractId : null)));
        }

        return tokenKeys;
    }

    private static TokenExpiryWrapper decodeTokenExpiry(
            @NonNull final Tuple expiryTuple, @NonNull final AddressIdConverter addressIdConverter) {

        // Expiry
        final int SECOND = 0;
        final int AUTO_RENEW_ACCOUNT = 1;
        final int AUTO_RENEW_PERIOD = 2;

        final var second = (long) expiryTuple.get(SECOND);
        final var autoRenewAccount = addressIdConverter.convert(expiryTuple.get(AUTO_RENEW_ACCOUNT));
        final var autoRenewPeriod = Duration.newBuilder()
                .seconds(expiryTuple.get(AUTO_RENEW_PERIOD))
                .build();
        return new TokenExpiryWrapper(
                second, autoRenewAccount.accountNum() == 0 ? null : autoRenewAccount, autoRenewPeriod);
    }

    public static List<FixedFeeWrapper> decodeFixedFees(
            @NonNull final Tuple[] fixedFeesTuples, @NonNull final AddressIdConverter addressIdConverter) {

        // FixedFee
        final int AMOUNT = 0;
        final int TOKEN_ID = 1;
        final int USE_HBARS_FOR_PAYMENTS = 2;
        final int USE_CURRENT_TOKEN_FOR_PAYMENT = 3;
        final int FEE_COLLECTOR = 4;

        final List<FixedFeeWrapper> fixedFees = new ArrayList<>(fixedFeesTuples.length);
        for (final var fixedFeeTuple : fixedFeesTuples) {
            final var amount = (long) fixedFeeTuple.get(AMOUNT);
            final var tokenId = ConversionUtils.asTokenId(fixedFeeTuple.get(TOKEN_ID));
            final var useHbarsForPayment = (Boolean) fixedFeeTuple.get(USE_HBARS_FOR_PAYMENTS);
            final var useCurrentTokenForPayment = (Boolean) fixedFeeTuple.get(USE_CURRENT_TOKEN_FOR_PAYMENT);
            final var feeCollector = addressIdConverter.convert(fixedFeeTuple.get(FEE_COLLECTOR));
            fixedFees.add(new FixedFeeWrapper(
                    amount,
                    tokenId.tokenNum() != 0 ? tokenId : null,
                    useHbarsForPayment,
                    useCurrentTokenForPayment,
                    feeCollector.accountNum() != 0 ? feeCollector : null));
        }
        return fixedFees;
    }

    public static List<FractionalFeeWrapper> decodeFractionalFees(
            @NonNull final Tuple[] fractionalFeesTuples, @NonNull final AddressIdConverter addressIdConverter) {

        // FractionalFee
        final int NUMERATOR = 0;
        final int DENOMINATOR = 1;
        final int MINIMUM_AMOUNT = 2;
        final int MAXIMUM_AMOUNT = 3;
        final int NET_OF_TRANSFERS = 4;
        final int FEE_COLLECTOR = 5;

        final List<FractionalFeeWrapper> fractionalFees = new ArrayList<>(fractionalFeesTuples.length);
        for (final var fractionalFeeTuple : fractionalFeesTuples) {
            final var numerator = (long) fractionalFeeTuple.get(NUMERATOR);
            final var denominator = (long) fractionalFeeTuple.get(DENOMINATOR);
            final var minimumAmount = (long) fractionalFeeTuple.get(MINIMUM_AMOUNT);
            final var maximumAmount = (long) fractionalFeeTuple.get(MAXIMUM_AMOUNT);
            final var netOfTransfers = (Boolean) fractionalFeeTuple.get(NET_OF_TRANSFERS);
            final var feeCollector = addressIdConverter.convert(fractionalFeeTuple.get(FEE_COLLECTOR));
            fractionalFees.add(new FractionalFeeWrapper(
                    numerator,
                    denominator,
                    minimumAmount,
                    maximumAmount,
                    netOfTransfers,
                    feeCollector.accountNum() != 0 ? feeCollector : null));
        }
        return fractionalFees;
    }

    public static List<RoyaltyFeeWrapper> decodeRoyaltyFees(
            @NonNull final Tuple[] royaltyFeesTuples, @NonNull final AddressIdConverter addressIdConverter) {

        // RoyaltyFee
        final int NUMERATOR = 0;
        final int DENOMINATOR = 1;
        final int FIXED_FEE_AMOUNT = 2;
        final int FIXED_FEE_TOKEN_ID = 3;
        final int FIXED_FEE_USE_HBARS = 4;

        final List<RoyaltyFeeWrapper> decodedRoyaltyFees = new ArrayList<>(royaltyFeesTuples.length);
        for (final var royaltyFeeTuple : royaltyFeesTuples) {
            final var numerator = (long) royaltyFeeTuple.get(NUMERATOR);
            final var denominator = (long) royaltyFeeTuple.get(DENOMINATOR);

            // When at least 1 of the following 3 values is different from its default value,
            // we treat it as though the user has tried to specify a fallbackFixedFee
            final var fixedFeeAmount = (long) royaltyFeeTuple.get(FIXED_FEE_AMOUNT);
            final var fixedFeeTokenId = ConversionUtils.asTokenId(royaltyFeeTuple.get(FIXED_FEE_TOKEN_ID));
            final var fixedFeeUseHbars = (Boolean) royaltyFeeTuple.get(FIXED_FEE_USE_HBARS);
            TokenCreateWrapper.FixedFeeWrapper fixedFee = null;
            if (fixedFeeAmount != 0 || fixedFeeTokenId.tokenNum() != 0 || Boolean.TRUE.equals(fixedFeeUseHbars)) {
                fixedFee = new TokenCreateWrapper.FixedFeeWrapper(
                        fixedFeeAmount,
                        fixedFeeTokenId.tokenNum() != 0 ? fixedFeeTokenId : null,
                        fixedFeeUseHbars,
                        false,
                        null);
            }

            final var feeCollector = addressIdConverter.convert(royaltyFeeTuple.get(5));
            decodedRoyaltyFees.add(new RoyaltyFeeWrapper(
                    numerator, denominator, fixedFee, feeCollector.accountNum() != 0 ? feeCollector : null));
        }
        return decodedRoyaltyFees;
    }
}
