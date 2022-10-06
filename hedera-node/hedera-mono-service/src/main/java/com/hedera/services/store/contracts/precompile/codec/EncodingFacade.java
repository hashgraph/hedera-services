/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.codec;

import static com.hedera.services.contracts.ParsingConstants.FunctionType.HAPI_MINT;
import static com.hedera.services.contracts.ParsingConstants.addressTuple;
import static com.hedera.services.contracts.ParsingConstants.bigIntegerTuple;
import static com.hedera.services.contracts.ParsingConstants.booleanTuple;
import static com.hedera.services.contracts.ParsingConstants.burnReturnType;
import static com.hedera.services.contracts.ParsingConstants.decimalsType;
import static com.hedera.services.contracts.ParsingConstants.getFungibleTokenInfoType;
import static com.hedera.services.contracts.ParsingConstants.getNonFungibleTokenInfoType;
import static com.hedera.services.contracts.ParsingConstants.getTokenCustomFeesType;
import static com.hedera.services.contracts.ParsingConstants.getTokenExpiryInfoType;
import static com.hedera.services.contracts.ParsingConstants.getTokenInfoType;
import static com.hedera.services.contracts.ParsingConstants.getTokenKeyType;
import static com.hedera.services.contracts.ParsingConstants.hapiAllowanceOfType;
import static com.hedera.services.contracts.ParsingConstants.intAddressTuple;
import static com.hedera.services.contracts.ParsingConstants.intBoolTuple;
import static com.hedera.services.contracts.ParsingConstants.intPairTuple;
import static com.hedera.services.contracts.ParsingConstants.intTuple;
import static com.hedera.services.contracts.ParsingConstants.mintReturnType;
import static com.hedera.services.contracts.ParsingConstants.notSpecifiedType;
import static com.hedera.services.contracts.ParsingConstants.stringTuple;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.contracts.ParsingConstants.FunctionType;
import com.hedera.services.store.contracts.precompile.TokenKeyType;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.RoyaltyFee;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.jetbrains.annotations.NotNull;

@Singleton
public class EncodingFacade {
    public static final Bytes SUCCESS_RESULT = resultFrom(SUCCESS);
    private static final long[] NO_MINTED_SERIAL_NUMBERS = new long[0];

    @Inject
    public EncodingFacade() {
        /* For Dagger2 */
    }

    public static Bytes resultFrom(@NotNull final ResponseCodeEnum status) {
        return UInt256.valueOf(status.getNumber());
    }

    public Bytes encodeTokenUri(final String tokenUri) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_TOKEN_URI)
                .withTokenUri(tokenUri)
                .build();
    }

    public Bytes encodeSymbol(final String symbol) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_SYMBOL)
                .withSymbol(symbol)
                .build();
    }

    public Bytes encodeName(final String name) {
        return functionResultBuilder().forFunction(FunctionType.ERC_NAME).withName(name).build();
    }

    public Bytes encodeOwner(final Address address) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_OWNER)
                .withOwner(address)
                .build();
    }

    public Bytes encodeGetApproved(final Address approved) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_GET_APPROVED)
                .withApproved(approved)
                .build();
    }

    public Bytes encodeGetApproved(final int status, final Address approved) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_GET_APPROVED)
                .withStatus(status)
                .withApproved(approved)
                .build();
    }

    public Bytes encodeBalance(final long balance) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_BALANCE)
                .withBalance(balance)
                .build();
    }

    public Bytes encodeIsFrozen(final boolean isFrozen) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_IS_FROZEN)
                .withStatus(SUCCESS.getNumber())
                .withIsFrozen(isFrozen)
                .build();
    }

    public Bytes encodeAllowance(final long allowance) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_ALLOWANCE)
                .withAllowance(allowance)
                .build();
    }

    public Bytes encodeAllowance(final int responseCode, final long allowance) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_ALLOWANCE)
                .withStatus(responseCode)
                .withAllowance(allowance)
                .build();
    }

    public Bytes encodeApprove(final boolean approve) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_APPROVE)
                .withApprove(approve)
                .build();
    }

    public Bytes encodeApprove(final int responseCode, final boolean approve) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_APPROVE)
                .withStatus(responseCode)
                .withApprove(approve)
                .build();
    }

    public Bytes encodeApproveNFT(final int responseCode) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_APPROVE_NFT)
                .withStatus(responseCode)
                .build();
    }

    public Bytes encodeDecimals(final int decimals) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_DECIMALS)
                .withDecimals(decimals)
                .build();
    }

    public Bytes encodeTotalSupply(final long totalSupply) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_TOTAL_SUPPLY)
                .withTotalSupply(totalSupply)
                .build();
    }

    public Bytes encodeMintSuccess(final long totalSupply, final long[] serialNumbers) {
        return functionResultBuilder()
                .forFunction(HAPI_MINT)
                .withStatus(SUCCESS.getNumber())
                .withTotalSupply(totalSupply)
                .withSerialNumbers(serialNumbers != null ? serialNumbers : NO_MINTED_SERIAL_NUMBERS)
                .build();
    }

    public Bytes encodeMintFailure(@NotNull final ResponseCodeEnum status) {
        return functionResultBuilder()
                .forFunction(HAPI_MINT)
                .withStatus(status.getNumber())
                .withTotalSupply(0L)
                .withSerialNumbers(NO_MINTED_SERIAL_NUMBERS)
                .build();
    }

    public Bytes encodeBurnSuccess(final long totalSupply) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_BURN)
                .withStatus(SUCCESS.getNumber())
                .withTotalSupply(totalSupply)
                .build();
    }

    public Bytes encodeBurnFailure(@NotNull final ResponseCodeEnum status) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_BURN)
                .withStatus(status.getNumber())
                .withTotalSupply(0L)
                .build();
    }

    public Bytes encodeEcFungibleTransfer(final boolean ercFungibleTransferStatus) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_TRANSFER)
                .withErcFungibleTransferStatus(ercFungibleTransferStatus)
                .build();
    }

    public Bytes encodeCreateSuccess(final Address newTokenAddress) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_CREATE)
                .withStatus(SUCCESS.getNumber())
                .withNewTokenAddress(newTokenAddress)
                .build();
    }

    public Bytes encodeCreateFailure(@NotNull final ResponseCodeEnum status) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_CREATE)
                .withStatus(status.getNumber())
                .withNewTokenAddress(Address.ZERO)
                .build();
    }

    public Bytes encodeIsApprovedForAll(final boolean isApprovedForAllStatus) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_IS_APPROVED_FOR_ALL)
                .withIsApprovedForAllStatus(isApprovedForAllStatus)
                .build();
    }

    public Bytes encodeIsApprovedForAll(final int status, final boolean isApprovedForAllStatus) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_IS_APPROVED_FOR_ALL)
                .withStatus(status)
                .withIsApprovedForAllStatus(isApprovedForAllStatus)
                .build();
    }

    public Bytes encodeGetTokenDefaultFreezeStatus(final boolean defaultFreezeStatus) {
        return functionResultBuilder()
                .forFunction(FunctionType.GET_TOKEN_DEFAULT_FREEZE_STATUS)
                .withStatus(SUCCESS.getNumber())
                .withGetTokenDefaultFreezeStatus(defaultFreezeStatus)
                .build();
    }

    public Bytes encodeGetTokenDefaultKycStatus(final boolean defaultKycStatus) {
        return functionResultBuilder()
                .forFunction(FunctionType.GET_TOKEN_DEFAULT_KYC_STATUS)
                .withStatus(SUCCESS.getNumber())
                .withGetTokenDefaultKycStatus(defaultKycStatus)
                .build();
    }

    public Bytes encodeIsKyc(final boolean isKyc) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_IS_KYC)
                .withStatus(SUCCESS.getNumber())
                .withIsKyc(isKyc)
                .build();
    }

    public Bytes encodeGetTokenInfo(final com.hederahashgraph.api.proto.java.TokenInfo tokenInfo) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_GET_TOKEN_INFO)
                .withStatus(SUCCESS.getNumber())
                .withTokenInfo(tokenInfo)
                .build();
    }

    public Bytes encodeGetFungibleTokenInfo(final TokenInfo tokenInfo) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_GET_FUNGIBLE_TOKEN_INFO)
                .withStatus(SUCCESS.getNumber())
                .withTokenInfo(tokenInfo)
                .build();
    }

    public Bytes encodeGetNonFungibleTokenInfo(
            final TokenInfo tokenInfo, final TokenNftInfo nonFungibleTokenInfo) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_GET_NON_FUNGIBLE_TOKEN_INFO)
                .withStatus(SUCCESS.getNumber())
                .withTokenInfo(tokenInfo)
                .withNftTokenInfo(nonFungibleTokenInfo)
                .build();
    }

    public Bytes encodeTokenGetCustomFees(final List<CustomFee> customFees) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_GET_TOKEN_CUSTOM_FEES)
                .withStatus(SUCCESS.getNumber())
                .withCustomFees(customFees)
                .build();
    }

    public Bytes encodeGetTokenKey(KeyValueWrapper keyValue) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_GET_TOKEN_KEY)
                .withStatus(SUCCESS.getNumber())
                .withKey(keyValue)
                .build();
    }

    public Bytes encodeIsToken(final boolean isToken) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_IS_TOKEN)
                .withStatus(SUCCESS.getNumber())
                .withIsToken(isToken)
                .build();
    }

    public Bytes encodeGetTokenType(final int tokenType) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_GET_TOKEN_TYPE)
                .withStatus(SUCCESS.getNumber())
                .withGetTokenType(tokenType)
                .build();
    }

    public Bytes encodeGetTokenExpiryInfo(final TokenExpiryWrapper tokenExpiryWrapper) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_GET_TOKEN_EXPIRY_INFO)
                .withStatus(SUCCESS.getNumber())
                .withExpiry(tokenExpiryWrapper)
                .build();
    }

    private FunctionResultBuilder functionResultBuilder() {
        return new FunctionResultBuilder();
    }

    private static class FunctionResultBuilder {
        private FunctionType functionType;
        private TupleType tupleType;
        private int status;
        private Address newTokenAddress;
        private boolean ercFungibleTransferStatus;
        private boolean isApprovedForAllStatus;
        private boolean tokenDefaultFreezeStatus;
        private boolean tokenDefaultKycStatus;
        private boolean isKyc;
        private long totalSupply;
        private long balance;
        private long allowance;
        private boolean approve;
        private long[] serialNumbers;
        private int decimals;
        private Address owner;
        private Address approved;
        private String name;
        private String symbol;
        private String metadata;
        private TokenInfo tokenInfo;
        private TokenNftInfo nonFungibleTokenInfo;
        private boolean isFrozen;
        private List<CustomFee> customFees;
        private boolean isToken;
        private int tokenType;
        private Tuple tokenExpiryInfo;
        private Tuple keyValue;

        private FunctionResultBuilder forFunction(final FunctionType functionType) {
            this.tupleType =
                    switch (functionType) {
                        case HAPI_CREATE, HAPI_GET_APPROVED -> intAddressTuple;
                        case HAPI_MINT -> mintReturnType;
                        case HAPI_BURN -> burnReturnType;
                        case ERC_TOTAL_SUPPLY, ERC_ALLOWANCE, ERC_BALANCE -> bigIntegerTuple;
                        case ERC_DECIMALS -> decimalsType;
                        case ERC_OWNER, ERC_GET_APPROVED -> addressTuple;
                        case ERC_NAME, ERC_TOKEN_URI, ERC_SYMBOL -> stringTuple;
                        case ERC_TRANSFER, ERC_IS_APPROVED_FOR_ALL, ERC_APPROVE -> booleanTuple;
                        case HAPI_ALLOWANCE -> hapiAllowanceOfType;
                        case HAPI_APPROVE,
                                HAPI_IS_TOKEN,
                                HAPI_IS_FROZEN,
                                GET_TOKEN_DEFAULT_KYC_STATUS,
                                GET_TOKEN_DEFAULT_FREEZE_STATUS,
                                HAPI_IS_KYC,
                                HAPI_IS_APPROVED_FOR_ALL -> intBoolTuple;
                        case HAPI_APPROVE_NFT -> intTuple;
                        case HAPI_GET_TOKEN_INFO -> getTokenInfoType;
                        case HAPI_GET_FUNGIBLE_TOKEN_INFO -> getFungibleTokenInfoType;
                        case HAPI_GET_NON_FUNGIBLE_TOKEN_INFO -> getNonFungibleTokenInfoType;
                        case HAPI_GET_TOKEN_CUSTOM_FEES -> getTokenCustomFeesType;
                        case HAPI_GET_TOKEN_KEY -> getTokenKeyType;
                        case HAPI_GET_TOKEN_TYPE -> intPairTuple;
                        case HAPI_GET_TOKEN_EXPIRY_INFO -> getTokenExpiryInfoType;
                        default -> notSpecifiedType;
                    };

            this.functionType = functionType;
            return this;
        }

        private FunctionResultBuilder withStatus(final int status) {
            this.status = status;
            return this;
        }

        private FunctionResultBuilder withNewTokenAddress(final Address newTokenAddress) {
            this.newTokenAddress = newTokenAddress;
            return this;
        }

        private FunctionResultBuilder withTotalSupply(final long totalSupply) {
            this.totalSupply = totalSupply;
            return this;
        }

        private FunctionResultBuilder withSerialNumbers(final long[] serialNumbers) {
            this.serialNumbers = serialNumbers;
            return this;
        }

        private FunctionResultBuilder withDecimals(final int decimals) {
            this.decimals = decimals;
            return this;
        }

        private FunctionResultBuilder withBalance(final long balance) {
            this.balance = balance;
            return this;
        }

        private FunctionResultBuilder withIsFrozen(final boolean isFrozen) {
            this.isFrozen = isFrozen;
            return this;
        }

        private FunctionResultBuilder withAllowance(final long allowance) {
            this.allowance = allowance;
            return this;
        }

        private FunctionResultBuilder withApprove(final boolean approve) {
            this.approve = approve;
            return this;
        }

        private FunctionResultBuilder withOwner(final Address address) {
            this.owner = address;
            return this;
        }

        private FunctionResultBuilder withApproved(final Address approved) {
            this.approved = approved;
            return this;
        }

        private FunctionResultBuilder withName(final String name) {
            this.name = name;
            return this;
        }

        private FunctionResultBuilder withSymbol(final String symbol) {
            this.symbol = symbol;
            return this;
        }

        private FunctionResultBuilder withTokenUri(final String tokenUri) {
            this.metadata = tokenUri;
            return this;
        }

        private FunctionResultBuilder withErcFungibleTransferStatus(
                final boolean ercFungibleTransferStatus) {
            this.ercFungibleTransferStatus = ercFungibleTransferStatus;
            return this;
        }

        private FunctionResultBuilder withIsApprovedForAllStatus(
                final boolean isApprovedForAllStatus) {
            this.isApprovedForAllStatus = isApprovedForAllStatus;
            return this;
        }

        private FunctionResultBuilder withTokenInfo(final TokenInfo tokenInfo) {
            this.tokenInfo = tokenInfo;
            return this;
        }

        private FunctionResultBuilder withNftTokenInfo(final TokenNftInfo nonFungibleTokenInfo) {
            this.nonFungibleTokenInfo = nonFungibleTokenInfo;
            return this;
        }

        private FunctionResultBuilder withIsKyc(final boolean isKyc) {
            this.isKyc = isKyc;
            return this;
        }

        private FunctionResultBuilder withGetTokenDefaultFreezeStatus(
                final boolean tokenDefaultFreezeStatus) {
            this.tokenDefaultFreezeStatus = tokenDefaultFreezeStatus;
            return this;
        }

        private FunctionResultBuilder withGetTokenDefaultKycStatus(
                final boolean tokenDefaultKycStatus) {
            this.tokenDefaultKycStatus = tokenDefaultKycStatus;
            return this;
        }

        private FunctionResultBuilder withCustomFees(final List<CustomFee> customFees) {
            this.customFees = customFees;
            return this;
        }

        private FunctionResultBuilder withIsToken(final boolean isToken) {
            this.isToken = isToken;
            return this;
        }

        private FunctionResultBuilder withGetTokenType(final int tokenType) {
            this.tokenType = tokenType;
            return this;
        }

        private FunctionResultBuilder withExpiry(final TokenExpiryWrapper tokenExpiryInfo) {
            this.tokenExpiryInfo =
                    Tuple.of(
                            tokenExpiryInfo.second(),
                            convertBesuAddressToHeadlongAddress(
                                    EntityIdUtils.asTypedEvmAddress(
                                            tokenExpiryInfo.autoRenewAccount())),
                            tokenExpiryInfo.autoRenewPeriod());
            return this;
        }

        private FunctionResultBuilder withKey(KeyValueWrapper wrapper) {
            this.keyValue =
                    Tuple.of(
                            wrapper.isShouldInheritAccountKeySet(),
                            convertBesuAddressToHeadlongAddress(
                                    EntityIdUtils.asTypedEvmAddress(wrapper.getContractID())),
                            wrapper.getEd25519Key(),
                            wrapper.getEcdsaSecp256k1(),
                            convertBesuAddressToHeadlongAddress(
                                    EntityIdUtils.asTypedEvmAddress(
                                            wrapper.getDelegatableContractID())));
            return this;
        }

        private Bytes build() {
            final var result =
                    switch (functionType) {
                        case HAPI_CREATE -> Tuple.of(
                                status, convertBesuAddressToHeadlongAddress(newTokenAddress));
                        case HAPI_MINT -> Tuple.of(
                                status, BigInteger.valueOf(totalSupply), serialNumbers);
                        case HAPI_BURN -> Tuple.of(status, BigInteger.valueOf(totalSupply));
                        case ERC_TOTAL_SUPPLY -> Tuple.of(BigInteger.valueOf(totalSupply));
                        case ERC_DECIMALS -> Tuple.of(decimals);
                        case ERC_BALANCE -> Tuple.of(BigInteger.valueOf(balance));
                        case ERC_OWNER -> Tuple.of(convertBesuAddressToHeadlongAddress(owner));
                        case ERC_NAME -> Tuple.of(name);
                        case ERC_SYMBOL -> Tuple.of(symbol);
                        case ERC_TOKEN_URI -> Tuple.of(metadata);
                        case ERC_TRANSFER -> Tuple.of(ercFungibleTransferStatus);
                        case ERC_ALLOWANCE -> Tuple.of(BigInteger.valueOf(allowance));
                        case ERC_APPROVE -> Tuple.of(approve);
                        case ERC_GET_APPROVED -> Tuple.of(
                                convertBesuAddressToHeadlongAddress(approved));
                        case ERC_IS_APPROVED_FOR_ALL -> Tuple.of(isApprovedForAllStatus);
                        case HAPI_APPROVE -> Tuple.of(status, approve);
                        case HAPI_APPROVE_NFT -> Tuple.of(status);
                        case HAPI_ALLOWANCE -> Tuple.of(status, BigInteger.valueOf(allowance));
                        case HAPI_GET_APPROVED -> Tuple.of(
                                status, convertBesuAddressToHeadlongAddress(approved));
                        case HAPI_IS_APPROVED_FOR_ALL -> Tuple.of(status, isApprovedForAllStatus);
                        case HAPI_GET_TOKEN_INFO -> getTupleForGetTokenInfo();
                        case HAPI_GET_FUNGIBLE_TOKEN_INFO -> getTupleForGetFungibleTokenInfo();
                        case HAPI_GET_NON_FUNGIBLE_TOKEN_INFO -> getTupleForGetNonFungibleTokenInfo();
                        case HAPI_IS_KYC -> Tuple.of(status, isKyc);
                        case GET_TOKEN_DEFAULT_FREEZE_STATUS -> Tuple.of(
                                status, tokenDefaultFreezeStatus);
                        case GET_TOKEN_DEFAULT_KYC_STATUS -> Tuple.of(
                                status, tokenDefaultKycStatus);
                        case HAPI_IS_FROZEN -> Tuple.of(status, isFrozen);
                        case HAPI_GET_TOKEN_CUSTOM_FEES -> getTupleForTokenGetCustomFees();
                        case HAPI_IS_TOKEN -> Tuple.of(status, isToken);
                        case HAPI_GET_TOKEN_TYPE -> Tuple.of(status, tokenType);
                        case HAPI_GET_TOKEN_EXPIRY_INFO -> getTupleForGetTokenExpiryInfo();
                        case HAPI_GET_TOKEN_KEY -> Tuple.of(status, keyValue);
                        default -> Tuple.of(status);
                    };

            return Bytes.wrap(tupleType.encode(result).array());
        }

        private Tuple getTupleForGetTokenInfo() {
            return Tuple.of(status, getTupleForTokenInfo());
        }

        private Tuple getTupleForTokenGetCustomFees() {
            return getTupleForTokenCustomFees(status);
        }

        private Tuple getTupleForGetTokenExpiryInfo() {
            return getTupleForTokenExpiryInfo(status);
        }

        private Tuple getTupleForGetFungibleTokenInfo() {
            return Tuple.of(status, Tuple.of(getTupleForTokenInfo(), tokenInfo.getDecimals()));
        }

        private Tuple getTupleForGetNonFungibleTokenInfo() {
            return Tuple.of(
                    status,
                    Tuple.of(
                            getTupleForTokenInfo(),
                            nonFungibleTokenInfo.getNftID().getSerialNumber(),
                            convertBesuAddressToHeadlongAddress(
                                    EntityIdUtils.asTypedEvmAddress(
                                            nonFungibleTokenInfo.getAccountID())),
                            nonFungibleTokenInfo.getCreationTime().getSeconds(),
                            nonFungibleTokenInfo.getMetadata().toByteArray(),
                            convertBesuAddressToHeadlongAddress(
                                    EntityIdUtils.asTypedEvmAddress(
                                            nonFungibleTokenInfo.getSpenderId()))));
        }

        private Tuple getTupleForTokenInfo() {
            final var fixedFees = new ArrayList<Tuple>();
            final var fractionalFees = new ArrayList<Tuple>();
            final var royaltyFees = new ArrayList<Tuple>();

            for (final var customFee : tokenInfo.getCustomFeesList()) {
                extractAllFees(fixedFees, fractionalFees, royaltyFees, customFee);
            }
            return Tuple.of(
                    getHederaTokenTuple(),
                    tokenInfo.getTotalSupply(),
                    tokenInfo.getDeleted(),
                    tokenInfo.getDefaultKycStatus().getNumber() == 1,
                    tokenInfo.getPauseStatus().getNumber() == 1,
                    fixedFees.toArray(new Tuple[fixedFees.size()]),
                    fractionalFees.toArray(new Tuple[fractionalFees.size()]),
                    royaltyFees.toArray(new Tuple[royaltyFees.size()]),
                    Bytes.wrap(tokenInfo.getLedgerId().toByteArray()).toString());
        }

        private Tuple getTupleForTokenCustomFees(final int responseCode) {
            final var fixedFees = new ArrayList<Tuple>();
            final var fractionalFees = new ArrayList<Tuple>();
            final var royaltyFees = new ArrayList<Tuple>();

            for (final var customFee : customFees) {
                extractAllFees(fixedFees, fractionalFees, royaltyFees, customFee);
            }
            return Tuple.of(
                    responseCode,
                    fixedFees.toArray(new Tuple[fixedFees.size()]),
                    fractionalFees.toArray(new Tuple[fractionalFees.size()]),
                    royaltyFees.toArray(new Tuple[royaltyFees.size()]));
        }

        private Tuple getTupleForTokenExpiryInfo(final int responseCode) {
            return Tuple.of(responseCode, tokenExpiryInfo);
        }

        private void extractAllFees(
                final ArrayList<Tuple> fixedFees,
                final ArrayList<Tuple> fractionalFees,
                final ArrayList<Tuple> royaltyFees,
                @NotNull final CustomFee customFee) {
            final var feeCollector =
                    convertBesuAddressToHeadlongAddress(
                            EntityIdUtils.asTypedEvmAddress(customFee.getFeeCollectorAccountId()));
            if (customFee.getFixedFee().getAmount() > 0) {
                fixedFees.add(getFixedFeeTuple(customFee.getFixedFee(), feeCollector));
            } else if (customFee.getFractionalFee().getMinimumAmount() > 0) {
                fractionalFees.add(
                        getFractionalFeeTuple(customFee.getFractionalFee(), feeCollector));
            } else if (customFee.getRoyaltyFee().getExchangeValueFraction().getNumerator() > 0) {
                royaltyFees.add(getRoyaltyFeeTuple(customFee.getRoyaltyFee(), feeCollector));
            }
        }

        private Tuple getFixedFeeTuple(
                @NotNull final FixedFee fixedFee,
                final com.esaulpaugh.headlong.abi.Address feeCollector) {
            return Tuple.of(
                    fixedFee.getAmount(),
                    convertBesuAddressToHeadlongAddress(
                            EntityIdUtils.asTypedEvmAddress(fixedFee.getDenominatingTokenId())),
                    fixedFee.getDenominatingTokenId().getTokenNum() == 0,
                    false,
                    feeCollector);
        }

        private Tuple getFractionalFeeTuple(
                @NotNull final FractionalFee fractionalFee,
                final com.esaulpaugh.headlong.abi.Address feeCollector) {
            return Tuple.of(
                    fractionalFee.getFractionalAmount().getNumerator(),
                    fractionalFee.getFractionalAmount().getDenominator(),
                    fractionalFee.getMinimumAmount(),
                    fractionalFee.getMaximumAmount(),
                    fractionalFee.getNetOfTransfers(),
                    feeCollector);
        }

        private Tuple getRoyaltyFeeTuple(
                @NotNull final RoyaltyFee royaltyFee,
                final com.esaulpaugh.headlong.abi.Address feeCollector) {
            return Tuple.of(
                    royaltyFee.getExchangeValueFraction().getNumerator(),
                    royaltyFee.getExchangeValueFraction().getDenominator(),
                    royaltyFee.getFallbackFee().getAmount(),
                    convertBesuAddressToHeadlongAddress(
                            EntityIdUtils.asTypedEvmAddress(
                                    royaltyFee.getFallbackFee().getDenominatingTokenId())),
                    royaltyFee.getFallbackFee().getDenominatingTokenId().getTokenNum() == 0,
                    feeCollector);
        }

        private Tuple getHederaTokenTuple() {
            final var expiry = tokenInfo.getExpiry().getSeconds();
            final var autoRenewPeriod = tokenInfo.getAutoRenewPeriod().getSeconds();
            final var expiryTuple =
                    Tuple.of(
                            expiry,
                            convertBesuAddressToHeadlongAddress(
                                    EntityIdUtils.asTypedEvmAddress(
                                            tokenInfo.getAutoRenewAccount())),
                            autoRenewPeriod);

            return Tuple.of(
                    tokenInfo.getName(),
                    tokenInfo.getSymbol(),
                    convertBesuAddressToHeadlongAddress(
                            EntityIdUtils.asTypedEvmAddress(tokenInfo.getTreasury())),
                    tokenInfo.getMemo(),
                    tokenInfo.getSupplyType().getNumber() == 1,
                    tokenInfo.getMaxSupply(),
                    tokenInfo.getDefaultFreezeStatus().getNumber() == 1,
                    getTokenKeysTuples(),
                    expiryTuple);
        }

        private Tuple[] getTokenKeysTuples() {
            final var adminKey = tokenInfo.getAdminKey();
            final var kycKey = tokenInfo.getKycKey();
            final var freezeKey = tokenInfo.getFreezeKey();
            final var wipeKey = tokenInfo.getWipeKey();
            final var supplyKey = tokenInfo.getSupplyKey();
            final var feeScheduleKey = tokenInfo.getFeeScheduleKey();
            final var pauseKey = tokenInfo.getPauseKey();

            final Tuple[] tokenKeysTuples = new Tuple[TokenKeyType.values().length];
            tokenKeysTuples[0] =
                    getKeyTuple(BigInteger.valueOf(TokenKeyType.ADMIN_KEY.value()), adminKey);
            tokenKeysTuples[1] =
                    getKeyTuple(BigInteger.valueOf(TokenKeyType.KYC_KEY.value()), kycKey);
            tokenKeysTuples[2] =
                    getKeyTuple(BigInteger.valueOf(TokenKeyType.FREEZE_KEY.value()), freezeKey);
            tokenKeysTuples[3] =
                    getKeyTuple(BigInteger.valueOf(TokenKeyType.WIPE_KEY.value()), wipeKey);
            tokenKeysTuples[4] =
                    getKeyTuple(BigInteger.valueOf(TokenKeyType.SUPPLY_KEY.value()), supplyKey);
            tokenKeysTuples[5] =
                    getKeyTuple(
                            BigInteger.valueOf(TokenKeyType.FEE_SCHEDULE_KEY.value()),
                            feeScheduleKey);
            tokenKeysTuples[6] =
                    getKeyTuple(BigInteger.valueOf(TokenKeyType.PAUSE_KEY.value()), pauseKey);

            return tokenKeysTuples;
        }

        private static Tuple getKeyTuple(final BigInteger keyType, @NotNull final Key key) {
            return Tuple.of(
                    keyType,
                    Tuple.of(
                            false,
                            key.getContractID().getContractNum() > 0
                                    ? convertBesuAddressToHeadlongAddress(
                                            EntityIdUtils.asTypedEvmAddress(key.getContractID()))
                                    : convertBesuAddressToHeadlongAddress(
                                            EntityIdUtils.asTypedEvmAddress(
                                                    ContractID.newBuilder()
                                                            .setShardNum(0L)
                                                            .setRealmNum(0L)
                                                            .setContractNum(0L)
                                                            .build())),
                            key.getEd25519().toByteArray(),
                            key.getECDSASecp256K1().toByteArray(),
                            key.getDelegatableContractId().getContractNum() > 0
                                    ? convertBesuAddressToHeadlongAddress(
                                            EntityIdUtils.asTypedEvmAddress(
                                                    key.getDelegatableContractId()))
                                    : convertBesuAddressToHeadlongAddress(
                                            EntityIdUtils.asTypedEvmAddress(
                                                    ContractID.newBuilder()
                                                            .setShardNum(0L)
                                                            .setRealmNum(0L)
                                                            .setContractNum(0L)
                                                            .build()))));
        }
    }

    public static class LogBuilder {
        private Address logger;
        private final List<Object> data = new ArrayList<>();
        private final List<LogTopic> topics = new ArrayList<>();
        final StringBuilder tupleTypes = new StringBuilder("(");

        public static LogBuilder logBuilder() {
            return new LogBuilder();
        }

        public LogBuilder forLogger(final Address logger) {
            this.logger = logger;
            return this;
        }

        public LogBuilder forEventSignature(final Bytes eventSignature) {
            topics.add(generateLogTopic(eventSignature));
            return this;
        }

        public LogBuilder forDataItem(final Object dataItem) {
            data.add(convertDataItem(dataItem));
            addTupleType(dataItem, tupleTypes);
            return this;
        }

        public LogBuilder forIndexedArgument(final Object param) {
            topics.add(generateLogTopic(param));
            return this;
        }

        public Log build() {
            if (tupleTypes.length() > 1) {
                tupleTypes.deleteCharAt(tupleTypes.length() - 1);
                tupleTypes.append(")");
                final var tuple = Tuple.of(data.toArray());
                final var tupleType = TupleType.parse(tupleTypes.toString());
                return new Log(logger, Bytes.wrap(tupleType.encode(tuple).array()), topics);
            } else {
                return new Log(logger, Bytes.EMPTY, topics);
            }
        }

        private Object convertDataItem(final Object param) {
            if (param instanceof Address address) {
                return convertBesuAddressToHeadlongAddress(address);
            } else if (param instanceof Long numeric) {
                return BigInteger.valueOf(numeric);
            } else {
                return param;
            }
        }

        private static LogTopic generateLogTopic(final Object param) {
            byte[] array = new byte[] {};
            if (param instanceof Address address) {
                array = address.toArray();
            } else if (param instanceof BigInteger numeric) {
                array = numeric.toByteArray();
            } else if (param instanceof Long numeric) {
                array = BigInteger.valueOf(numeric).toByteArray();
            } else if (param instanceof Boolean bool) {
                array = new byte[] {(byte) (Boolean.TRUE.equals(bool) ? 1 : 0)};
            } else if (param instanceof Bytes bytes) {
                array = bytes.toArray();
            }

            return LogTopic.wrap(Bytes.wrap(expandByteArrayTo32Length(array)));
        }

        private static void addTupleType(final Object param, final StringBuilder stringBuilder) {
            if (param instanceof Address) {
                stringBuilder.append("address,");
            } else if (param instanceof BigInteger || param instanceof Long) {
                stringBuilder.append("uint256,");
            } else if (param instanceof Boolean) {
                stringBuilder.append("bool,");
            }
        }

        private static byte[] expandByteArrayTo32Length(final byte[] bytesToExpand) {
            byte[] expandedArray = new byte[32];

            System.arraycopy(
                    bytesToExpand,
                    0,
                    expandedArray,
                    expandedArray.length - bytesToExpand.length,
                    bytesToExpand.length);
            return expandedArray;
        }
    }

    static com.esaulpaugh.headlong.abi.Address convertBesuAddressToHeadlongAddress(
            @NotNull final Address address) {
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(
                        address.toUnsignedBigInteger()));
    }
}
