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
package com.hedera.services.bdd.suites.utils.contracts.precompile;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.bdd.suites.utils.contracts.ContractCallResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class HTSPrecompileResult implements ContractCallResult {
    private HTSPrecompileResult() {}

    // data types
    public static final String ADDRESS = "(address)";
    public static final String ARRAY_BRACKETS = "[]";
    public static final String BOOL = "(bool)";
    public static final String BYTES32 = "(bytes32)";
    public static final String BYTES32_PAIR_RAW_TYPE = "(bytes32,bytes32)";
    public static final String INT = "(int)";
    public static final String STRING = "(string)";
    public static final String UINT8 = "(uint8)";
    public static final String UINT256 = "(uint256)";

    // struct types
    public static final String EXPIRY = "(uint32,address,uint32)";
    public static final String FIXED_FEE = "(uint32,address,bool,bool,address)";
    public static final String FRACTIONAL_FEE = "(uint32,uint32,uint32,uint32,bool,address)";
    public static final String KEY_VALUE = "(bool,address,bytes,bytes,address)";
    public static final String ROYALTY_FEE = "(uint32,uint32,uint32,address,bool,address)";
    public static final String TOKEN_KEY = "(uint256," + KEY_VALUE + ")";

    private static final String RESPONSE_STATUS_AT_BEGINNING = "(int32,";
    private static final String HEDERA_TOKEN =
            "("
                    + "string,string,address,string,bool,int64,bool,"
                    + TOKEN_KEY
                    + ARRAY_BRACKETS
                    + ","
                    + EXPIRY
                    + ")";
    private static final String TOKEN_INFO =
            "("
                    + HEDERA_TOKEN
                    + ",int64,bool,bool,bool,"
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE
                    + ARRAY_BRACKETS
                    + ",string"
                    + ")";
    private static final String FUNGIBLE_TOKEN_INFO = "(" + TOKEN_INFO + ",int32" + ")";
    private static final String NON_FUNGIBLE_TOKEN_INFO =
            "(" + TOKEN_INFO + ",int64,address,int64,bytes,address" + ")";

    private static final TupleType mintReturnType = TupleType.parse("(int32,uint64,int64[])");
    private static final TupleType notSpecifiedType = TupleType.parse("(int32)");
    private static final TupleType burnReturnType = TupleType.parse("(int32,uint64)");
    private static final TupleType totalSupplyType = TupleType.parse("(uint256)");
    private static final TupleType balanceOfType = TupleType.parse("(uint256)");
    private static final TupleType allowanceOfType = TupleType.parse("(uint256)");
    private static final TupleType decimalsType = TupleType.parse("(uint8)");
    private static final TupleType ownerOfType = TupleType.parse("(address)");
    private static final TupleType getApprovedType = TupleType.parse("(address)");
    private static final TupleType nameType = TupleType.parse("(string)");
    private static final TupleType symbolType = TupleType.parse("(string)");
    private static final TupleType tokenUriType = TupleType.parse("(string)");
    private static final TupleType ercTransferType = TupleType.parse("(bool)");
    private static final TupleType isApprovedForAllType = TupleType.parse("(bool)");
    private static final TupleType getTokenInfoType =
            TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + TOKEN_INFO + ")");
    private static final TupleType getFungibleTokenInfoType =
            TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + FUNGIBLE_TOKEN_INFO + ")");
    private static final TupleType getNonFungibleTokenInfoType =
            TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + NON_FUNGIBLE_TOKEN_INFO + ")");

    public static HTSPrecompileResult htsPrecompileResult() {
        return new HTSPrecompileResult();
    }

    public enum FunctionType {
        MINT,
        BURN,
        TOTAL_SUPPLY,
        DECIMALS,
        BALANCE,
        OWNER,
        TOKEN_URI,
        NAME,
        SYMBOL,
        ERC_TRANSFER,
        NOT_SPECIFIED,
        ALLOWANCE,
        IS_APPROVED_FOR_ALL,
        GET_APPROVED,
        GET_TOKEN_INFO,
        GET_FUNGIBLE_TOKEN_INFO,
        GET_NON_FUNGIBLE_TOKEN_INFO
    }

    private FunctionType functionType = FunctionType.NOT_SPECIFIED;
    private TupleType tupleType = notSpecifiedType;
    private ResponseCodeEnum status;
    private long totalSupply;
    private long[] serialNumbers;
    private long serialNumber;
    private int decimals;
    private long creationTime;
    private byte[] owner;
    private byte[] spender;
    private String name;
    private String symbol;
    private String metadata;
    private long balance;
    private long allowance;
    private boolean ercFungibleTransferStatus;
    private boolean isApprovedForAllStatus;
    private TokenInfo tokenInfo;

    public HTSPrecompileResult forFunction(final FunctionType functionType) {
        switch (functionType) {
            case MINT -> tupleType = mintReturnType;
            case BURN -> tupleType = burnReturnType;
            case TOTAL_SUPPLY -> tupleType = totalSupplyType;
            case DECIMALS -> tupleType = decimalsType;
            case BALANCE -> tupleType = balanceOfType;
            case OWNER -> tupleType = ownerOfType;
            case GET_APPROVED -> tupleType = getApprovedType;
            case NAME -> tupleType = nameType;
            case SYMBOL -> tupleType = symbolType;
            case TOKEN_URI -> tupleType = tokenUriType;
            case ERC_TRANSFER -> tupleType = ercTransferType;
            case ALLOWANCE -> tupleType = allowanceOfType;
            case IS_APPROVED_FOR_ALL -> tupleType = isApprovedForAllType;
            case GET_TOKEN_INFO -> tupleType = getTokenInfoType;
            case GET_FUNGIBLE_TOKEN_INFO -> tupleType = getFungibleTokenInfoType;
            case GET_NON_FUNGIBLE_TOKEN_INFO -> tupleType = getNonFungibleTokenInfoType;
        }

        this.functionType = functionType;
        return this;
    }

    public HTSPrecompileResult withStatus(final ResponseCodeEnum status) {
        this.status = status;
        return this;
    }

    public HTSPrecompileResult withTotalSupply(final long totalSupply) {
        this.totalSupply = totalSupply;
        return this;
    }

    public HTSPrecompileResult withSerialNumbers(final long... serialNumbers) {
        this.serialNumbers = serialNumbers;
        return this;
    }

    public HTSPrecompileResult withDecimals(final int decimals) {
        this.decimals = decimals;
        return this;
    }

    public HTSPrecompileResult withBalance(final long balance) {
        this.balance = balance;
        return this;
    }

    public HTSPrecompileResult withOwner(final byte[] address) {
        this.owner = address;
        return this;
    }

    public HTSPrecompileResult withSpender(final byte[] spender) {
        this.spender = spender;
        return this;
    }

    public HTSPrecompileResult withName(final String name) {
        this.name = name;
        return this;
    }

    public HTSPrecompileResult withSymbol(final String symbol) {
        this.symbol = symbol;
        return this;
    }

    public HTSPrecompileResult withTokenUri(final String tokenUri) {
        this.metadata = tokenUri;
        return this;
    }

    public HTSPrecompileResult withSerialNumber(final long serialNumber) {
        this.serialNumber = serialNumber;
        return this;
    }

    public HTSPrecompileResult withCreationTime(final long creationTime) {
        this.creationTime = creationTime;
        return this;
    }

    public HTSPrecompileResult withErcFungibleTransferStatus(
            final boolean ercFungibleTransferStatus) {
        this.ercFungibleTransferStatus = ercFungibleTransferStatus;
        return this;
    }

    public HTSPrecompileResult withAllowance(final long allowance) {
        this.allowance = allowance;
        return this;
    }

    public HTSPrecompileResult withIsApprovedForAll(final boolean isApprovedForAllStatus) {
        this.isApprovedForAllStatus = isApprovedForAllStatus;
        return this;
    }

    public HTSPrecompileResult withTokenInfo(final TokenInfo tokenInfo) {
        this.tokenInfo = tokenInfo;
        return this;
    }

    @Override
    public Bytes getBytes() {
        Tuple result;

        switch (functionType) {
            case MINT -> result =
                    Tuple.of(status.getNumber(), BigInteger.valueOf(totalSupply), serialNumbers);
            case BURN -> result = Tuple.of(status.getNumber(), BigInteger.valueOf(totalSupply));
            case TOTAL_SUPPLY -> result = Tuple.of(BigInteger.valueOf(totalSupply));
            case DECIMALS -> result = Tuple.of(decimals);
            case BALANCE -> result = Tuple.of(BigInteger.valueOf(balance));
            case OWNER -> {
                return Bytes.wrap(expandByteArrayTo32Length(owner));
            }
            case GET_APPROVED -> {
                return Bytes.wrap(expandByteArrayTo32Length(spender));
            }
            case NAME -> result = Tuple.of(name);
            case SYMBOL -> result = Tuple.of(symbol);
            case TOKEN_URI -> result = Tuple.of(metadata);
            case ERC_TRANSFER -> result = Tuple.of(ercFungibleTransferStatus);
            case IS_APPROVED_FOR_ALL -> result = Tuple.of(isApprovedForAllStatus);
            case ALLOWANCE -> result = Tuple.of(BigInteger.valueOf(allowance));
            case GET_TOKEN_INFO -> result = getTupleForGetTokenInfo();
            case GET_FUNGIBLE_TOKEN_INFO -> result = getTupleForGetFungibleTokenInfo();
            case GET_NON_FUNGIBLE_TOKEN_INFO -> result = getTupleForGetNonFungibleTokenInfo();
            default -> result = Tuple.of(status.getNumber());
        }
        return Bytes.wrap(tupleType.encode(result).array());
    }

    private Tuple getTupleForGetTokenInfo() {
        return Tuple.of(status.getNumber(), getTupleForTokenInfo());
    }

    private Tuple getTupleForGetFungibleTokenInfo() {
        return Tuple.of(status.getNumber(), Tuple.of(getTupleForTokenInfo(), decimals));
    }

    private Tuple getTupleForGetNonFungibleTokenInfo() {
        return Tuple.of(
                status.getNumber(),
                Tuple.of(
                        getTupleForTokenInfo(),
                        serialNumber,
                        convertBesuAddressToHeadlongAddress(Address.wrap(Bytes.wrap(owner))),
                        creationTime,
                        metadata.getBytes(),
                        convertBesuAddressToHeadlongAddress(Address.wrap(Bytes.wrap(spender)))));
    }

    private Tuple getTupleForTokenInfo() {
        return Tuple.of(
                getHederaTokenTuple(),
                tokenInfo.totalSupply(),
                tokenInfo.deleted(),
                tokenInfo.defaultKycStatus(),
                tokenInfo.pauseStatus(),
                getFixedFeesTuples(),
                getFractionalFeesTuples(),
                getRoyaltyFeesTuples(),
                tokenInfo.ledgerId());
    }

    private Tuple[] getFixedFeesTuples() {
        final var fixedFees = tokenInfo.fixedFees();
        final Tuple[] fixedFeesTuples = new Tuple[fixedFees.size()];
        for (int i = 0; i < fixedFees.size(); i++) {
            final var fixedFee = fixedFees.get(i);
            final var fixedFeeTuple =
                    Tuple.of(
                            fixedFee.amount(),
                            fixedFee.tokenId() != null
                                    ? convertBesuAddressToHeadlongAddress(fixedFee.tokenId())
                                    : convertBesuAddressToHeadlongAddress(Address.ZERO),
                            fixedFee.useHbarsForPayment(),
                            fixedFee.useCurrentTokenForPayment(),
                            fixedFee.feeCollector() != null
                                    ? convertBesuAddressToHeadlongAddress(fixedFee.feeCollector())
                                    : convertBesuAddressToHeadlongAddress(Address.ZERO));
            fixedFeesTuples[i] = fixedFeeTuple;
        }

        return fixedFeesTuples;
    }

    private Tuple[] getFractionalFeesTuples() {
        final var fractionalFees = tokenInfo.fractionalFees();
        final Tuple[] fractionalFeesTuples = new Tuple[fractionalFees.size()];
        for (int i = 0; i < fractionalFees.size(); i++) {
            final var fractionalFee = fractionalFees.get(i);
            final var fractionalFeeTuple =
                    Tuple.of(
                            fractionalFee.numerator(),
                            fractionalFee.denominator(),
                            fractionalFee.minimumAmount(),
                            fractionalFee.maximumAmount(),
                            fractionalFee.netOfTransfers(),
                            fractionalFee.feeCollector() != null
                                    ? convertBesuAddressToHeadlongAddress(
                                            fractionalFee.feeCollector())
                                    : convertBesuAddressToHeadlongAddress(Address.ZERO));
            fractionalFeesTuples[i] = fractionalFeeTuple;
        }

        return fractionalFeesTuples;
    }

    private Tuple[] getRoyaltyFeesTuples() {
        final var royaltyFees = tokenInfo.royaltyFees();
        final Tuple[] royaltyFeesTuples = new Tuple[royaltyFees.size()];
        for (int i = 0; i < royaltyFees.size(); i++) {
            final var royaltyFee = royaltyFees.get(i);
            final var royaltyFeeTuple =
                    Tuple.of(
                            royaltyFee.numerator(),
                            royaltyFee.denominator(),
                            royaltyFee.amount(),
                            royaltyFee.tokenId() != null
                                    ? convertBesuAddressToHeadlongAddress(royaltyFee.tokenId())
                                    : convertBesuAddressToHeadlongAddress(Address.ZERO),
                            royaltyFee.useHbarsForPayment(),
                            royaltyFee.feeCollector() != null
                                    ? convertBesuAddressToHeadlongAddress(royaltyFee.feeCollector())
                                    : convertBesuAddressToHeadlongAddress(Address.ZERO));
            royaltyFeesTuples[i] = royaltyFeeTuple;
        }

        return royaltyFeesTuples;
    }

    private Tuple getHederaTokenTuple() {
        final var hederaToken = tokenInfo.token();
        final var expiry = hederaToken.expiry();
        final var expiryTuple =
                Tuple.of(
                        expiry.second(),
                        convertBesuAddressToHeadlongAddress(expiry.autoRenewAccount()),
                        expiry.autoRenewPeriod());

        return Tuple.of(
                hederaToken.name(),
                hederaToken.symbol(),
                convertBesuAddressToHeadlongAddress(hederaToken.treasury()),
                hederaToken.memo(),
                hederaToken.tokenSupplyType(),
                hederaToken.maxSupply(),
                hederaToken.freezeDefault(),
                getTokenKeysTuples(),
                expiryTuple);
    }

    private Tuple[] getTokenKeysTuples() {
        final var hederaToken = tokenInfo.token();
        final var tokenKeys = hederaToken.tokenKeys();
        final Tuple[] tokenKeysTuples = new Tuple[tokenKeys.size()];
        for (int i = 0; i < tokenKeys.size(); i++) {
            final var key = tokenKeys.get(i);
            final var keyValue = key.key();
            Tuple keyValueTuple =
                    Tuple.of(
                            keyValue.inheritAccountKey(),
                            keyValue.contractId() != null
                                    ? keyValue.contractId()
                                    : convertBesuAddressToHeadlongAddress(Address.ZERO),
                            keyValue.ed25519(),
                            keyValue.ECDSA_secp256k1(),
                            keyValue.delegatableContractId() != null
                                    ? keyValue.delegatableContractId()
                                    : convertBesuAddressToHeadlongAddress(Address.ZERO));
            tokenKeysTuples[i] = (Tuple.of(BigInteger.valueOf(key.keyType()), keyValueTuple));
        }

        return tokenKeysTuples;
    }

    static com.esaulpaugh.headlong.abi.Address convertBesuAddressToHeadlongAddress(
            final Address address) {
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(
                        address.toUnsignedBigInteger()));
    }

    public static byte[] expandByteArrayTo32Length(final byte[] bytesToExpand) {
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
