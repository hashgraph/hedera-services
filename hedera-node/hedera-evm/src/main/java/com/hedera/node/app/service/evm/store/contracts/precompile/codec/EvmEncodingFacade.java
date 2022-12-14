/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.addressTuple;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.bigIntegerTuple;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.booleanTuple;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.decimalsType;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.getTokenInfoType;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.intBoolTuple;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.intPairTuple;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.notSpecifiedType;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.stringTuple;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.FunctionType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@Singleton
public class EvmEncodingFacade {

    @Inject
    public EvmEncodingFacade() {
        /* For Dagger2 */
    }

    public Bytes encodeDecimals(final int decimals) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_DECIMALS)
                .withDecimals(decimals)
                .build();
    }

    public Bytes encodeGetTokenType(final int tokenType) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_GET_TOKEN_TYPE)
                .withStatus(SUCCESS.getNumber())
                .withGetTokenType(tokenType)
                .build();
    }

    public Bytes encodeAllowance(final long allowance) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_ALLOWANCE)
                .withAllowance(allowance)
                .build();
    }

    public Bytes encodeTotalSupply(final long totalSupply) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_TOTAL_SUPPLY)
                .withTotalSupply(totalSupply)
                .build();
    }

    public Bytes encodeBalance(final long balance) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_BALANCE)
                .withBalance(balance)
                .build();
    }

    public Bytes encodeIsApprovedForAll(final boolean isApprovedForAllStatus) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_IS_APPROVED_FOR_ALL)
                .withIsApprovedForAllStatus(isApprovedForAllStatus)
                .build();
    }

    public Bytes encodeIsFrozen(final boolean isFrozen) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_IS_FROZEN)
                .withStatus(SUCCESS.getNumber())
                .withIsFrozen(isFrozen)
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

    public Bytes encodeIsToken(final boolean isToken) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_IS_TOKEN)
                .withStatus(SUCCESS.getNumber())
                .withIsToken(isToken)
                .build();
    }

    public Bytes encodeName(final String name) {
        return functionResultBuilder().forFunction(FunctionType.ERC_NAME).withName(name).build();
    }

    public Bytes encodeSymbol(final String symbol) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_SYMBOL)
                .withSymbol(symbol)
                .build();
    }

    public Bytes encodeTokenUri(final String tokenUri) {
        return functionResultBuilder()
                .forFunction(FunctionType.ERC_TOKEN_URI)
                .withTokenUri(tokenUri)
                .build();
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

    public Bytes encodeGetTokenInfo(final EvmTokenInfo tokenInfo) {
        return functionResultBuilder()
                .forFunction(FunctionType.HAPI_GET_TOKEN_INFO)
                .withStatus(SUCCESS.getNumber())
                .withTokenInfo(tokenInfo)
                .build();
    }

    private FunctionResultBuilder functionResultBuilder() {
        return new FunctionResultBuilder();
    }

    private static class FunctionResultBuilder {

        private FunctionType functionType;
        private TupleType tupleType;

        private int status;
        private int decimals;
        private int tokenType;

        private long allowance;
        private long totalSupply;
        private long balance;

        private boolean isApprovedForAllStatus;
        private boolean isFrozen;
        private boolean tokenDefaultFreezeStatus;
        private boolean tokenDefaultKycStatus;
        private boolean isKyc;
        private boolean isToken;

        private String name;
        private String symbol;
        private String metadata;

        private Address owner;
        private Address approved;

        private EvmTokenInfo tokenInfo;

        private FunctionResultBuilder forFunction(final FunctionType functionType) {
            this.tupleType =
                    switch (functionType) {
                        case ERC_DECIMALS -> decimalsType;
                        case HAPI_GET_TOKEN_TYPE -> intPairTuple;
                        case ERC_ALLOWANCE, ERC_TOTAL_SUPPLY, ERC_BALANCE -> bigIntegerTuple;
                        case ERC_IS_APPROVED_FOR_ALL -> booleanTuple;
                        case HAPI_IS_FROZEN,
                                GET_TOKEN_DEFAULT_FREEZE_STATUS,
                                GET_TOKEN_DEFAULT_KYC_STATUS,
                                HAPI_IS_KYC,
                                HAPI_IS_TOKEN -> intBoolTuple;
                        case ERC_NAME, ERC_SYMBOL, ERC_TOKEN_URI -> stringTuple;
                        case ERC_OWNER, ERC_GET_APPROVED -> addressTuple;
                        case HAPI_GET_TOKEN_INFO -> getTokenInfoType;
                        default -> notSpecifiedType;
                    };
            this.functionType = functionType;
            return this;
        }

        private FunctionResultBuilder withStatus(final int status) {
            this.status = status;
            return this;
        }

        private FunctionResultBuilder withDecimals(final int decimals) {
            this.decimals = decimals;
            return this;
        }

        private FunctionResultBuilder withGetTokenType(final int tokenType) {
            this.tokenType = tokenType;
            return this;
        }

        private FunctionResultBuilder withAllowance(final long allowance) {
            this.allowance = allowance;
            return this;
        }

        private FunctionResultBuilder withBalance(final long balance) {
            this.balance = balance;
            return this;
        }

        private FunctionResultBuilder withTotalSupply(final long totalSupply) {
            this.totalSupply = totalSupply;
            return this;
        }

        private FunctionResultBuilder withIsApprovedForAllStatus(
                final boolean isApprovedForAllStatus) {
            this.isApprovedForAllStatus = isApprovedForAllStatus;
            return this;
        }

        private FunctionResultBuilder withIsFrozen(final boolean isFrozen) {
            this.isFrozen = isFrozen;
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

        private FunctionResultBuilder withIsKyc(final boolean isKyc) {
            this.isKyc = isKyc;
            return this;
        }

        private FunctionResultBuilder withIsToken(final boolean isToken) {
            this.isToken = isToken;
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

        private FunctionResultBuilder withOwner(final Address address) {
            this.owner = address;
            return this;
        }

        private FunctionResultBuilder withApproved(final Address approved) {
            this.approved = approved;
            return this;
        }

        private FunctionResultBuilder withTokenInfo(final EvmTokenInfo tokenInfo) {
            this.tokenInfo = tokenInfo;
            return this;
        }

        private Bytes build() {
            final var result =
                    switch (functionType) {
                        case ERC_DECIMALS -> Tuple.of(decimals);
                        case ERC_ALLOWANCE -> Tuple.of(BigInteger.valueOf(allowance));
                        case ERC_TOTAL_SUPPLY -> Tuple.of(BigInteger.valueOf(totalSupply));
                        case ERC_BALANCE -> Tuple.of(BigInteger.valueOf(balance));
                        case ERC_IS_APPROVED_FOR_ALL -> Tuple.of(isApprovedForAllStatus);
                        case HAPI_IS_FROZEN -> Tuple.of(status, isFrozen);
                        case GET_TOKEN_DEFAULT_FREEZE_STATUS -> Tuple.of(
                                status, tokenDefaultFreezeStatus);
                        case GET_TOKEN_DEFAULT_KYC_STATUS -> Tuple.of(
                                status, tokenDefaultKycStatus);
                        case HAPI_IS_KYC -> Tuple.of(status, isKyc);
                        case HAPI_IS_TOKEN -> Tuple.of(status, isToken);
                        case HAPI_GET_TOKEN_TYPE -> Tuple.of(status, tokenType);
                        case ERC_NAME -> Tuple.of(name);
                        case ERC_SYMBOL -> Tuple.of(symbol);
                        case ERC_TOKEN_URI -> Tuple.of(metadata);
                        case ERC_OWNER -> Tuple.of(convertBesuAddressToHeadlongAddress(owner));
                        case ERC_GET_APPROVED -> Tuple.of(
                                convertBesuAddressToHeadlongAddress(approved));
                        case HAPI_GET_TOKEN_INFO -> getTupleForGetTokenInfo();
                        default -> Tuple.of(status);
                    };

            return Bytes.wrap(tupleType.encode(result).array());
        }

        private Tuple getTupleForGetTokenInfo() {
            return Tuple.of(status, getTupleForTokenInfo());
        }

        private Tuple getTupleForTokenInfo() {
            final var fixedFees = new ArrayList<Tuple>();
            final var fractionalFees = new ArrayList<Tuple>();
            final var royaltyFees = new ArrayList<Tuple>();

            for (final var customFee : tokenInfo.getCustomFees()) {
                extractAllFees(fixedFees, fractionalFees, royaltyFees, customFee);
            }

            return Tuple.of(
                    getHederaTokenTuple(),
                    tokenInfo.getTotalSupply(),
                    tokenInfo.isDeleted(),
                    tokenInfo.getDefaultKycStatus() == 1,
                    tokenInfo.getPauseStatus() == 1,
                    fixedFees.toArray(new Tuple[fixedFees.size()]),
                    fractionalFees.toArray(new Tuple[fractionalFees.size()]),
                    royaltyFees.toArray(new Tuple[royaltyFees.size()]),
                    Bytes.wrap(tokenInfo.getLedgerId()).toString());
        }

        private Tuple getHederaTokenTuple() {
            final var expiry = tokenInfo.getExpiry();
            final var autoRenewPeriod = tokenInfo.getAutoRenewPeriod();
            final var expiryTuple =
                    Tuple.of(
                            expiry,
                            convertBesuAddressToHeadlongAddress(tokenInfo.getAutoRenewAccount()),
                            autoRenewPeriod);

            return Tuple.of(
                    tokenInfo.getName(),
                    tokenInfo.getSymbol(),
                    convertBesuAddressToHeadlongAddress(tokenInfo.getTreasury()),
                    tokenInfo.getMemo(),
                    tokenInfo.getSupplyType() == 1,
                    tokenInfo.getMaxSupply(),
                    tokenInfo.getDefaultFreezeStatus() == 1,
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

        private static Tuple getKeyTuple(final BigInteger keyType, @NonNull final EvmKey key) {
            return Tuple.of(
                    keyType,
                    Tuple.of(
                            false,
                            convertBesuAddressToHeadlongAddress(key.getContractId()),
                            key.getEd25519(),
                            key.getECDSA_secp256k1(),
                            convertBesuAddressToHeadlongAddress(key.getDelegatableContractId())));
        }

        private void extractAllFees(
                final ArrayList<Tuple> fixedFees,
                final ArrayList<Tuple> fractionalFees,
                final ArrayList<Tuple> royaltyFees,
                @NonNull final CustomFee customFee) {
            if (customFee.getFixedFee() != null) {
                fixedFees.add(getFixedFeeTuple(customFee.getFixedFee()));
            } else if (customFee.getFractionalFee() != null) {
                fractionalFees.add(getFractionalFeeTuple(customFee.getFractionalFee()));
            } else if (customFee.getRoyaltyFee() != null) {
                royaltyFees.add(getRoyaltyFeeTuple(customFee.getRoyaltyFee()));
            }
        }

        private Tuple getFixedFeeTuple(@NonNull final FixedFee fixedFee) {
            return Tuple.of(
                    fixedFee.getAmount(),
                    convertBesuAddressToHeadlongAddress(fixedFee.getDenominatingTokenId()),
                    fixedFee.isUseHbarsForPayment(),
                    fixedFee.isUseCurrentTokenForPayment(),
                    convertBesuAddressToHeadlongAddress(fixedFee.getFeeCollector()));
        }

        private Tuple getFractionalFeeTuple(@NonNull final FractionalFee fractionalFee) {
            return Tuple.of(
                    fractionalFee.getNumerator(),
                    fractionalFee.getDenominator(),
                    fractionalFee.getMinimumAmount(),
                    fractionalFee.getMaximumAmount(),
                    fractionalFee.getNetOfTransfers(),
                    convertBesuAddressToHeadlongAddress(fractionalFee.getFeeCollector()));
        }

        private Tuple getRoyaltyFeeTuple(@NonNull final RoyaltyFee royaltyFee) {
            return Tuple.of(
                    royaltyFee.getNumerator(),
                    royaltyFee.getDenominator(),
                    royaltyFee.getAmount(),
                    convertBesuAddressToHeadlongAddress(royaltyFee.getDenominatingTokenId()),
                    royaltyFee.isUseHbarsForPayment(),
                    convertBesuAddressToHeadlongAddress(royaltyFee.getFeeCollector()));
        }
    }

    static com.esaulpaugh.headlong.abi.Address convertBesuAddressToHeadlongAddress(
            @NonNull final Address address) {
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(
                        address.toUnsignedBigInteger()));
    }
}
