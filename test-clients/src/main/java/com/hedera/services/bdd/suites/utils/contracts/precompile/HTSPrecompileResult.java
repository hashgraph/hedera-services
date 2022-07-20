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

import static com.hedera.services.parsing.ParsingConstants.allowanceOfType;
import static com.hedera.services.parsing.ParsingConstants.balanceOfType;
import static com.hedera.services.parsing.ParsingConstants.burnReturnType;
import static com.hedera.services.parsing.ParsingConstants.decimalsType;
import static com.hedera.services.parsing.ParsingConstants.ercTransferType;
import static com.hedera.services.parsing.ParsingConstants.getApprovedType;
import static com.hedera.services.parsing.ParsingConstants.getFungibleTokenInfoTypeReplacedAddress;
import static com.hedera.services.parsing.ParsingConstants.getNonFungibleTokenInfoTypeReplacedAddress;
import static com.hedera.services.parsing.ParsingConstants.getTokenInfoTypeReplacedAddress;
import static com.hedera.services.parsing.ParsingConstants.isApprovedForAllType;
import static com.hedera.services.parsing.ParsingConstants.mintReturnType;
import static com.hedera.services.parsing.ParsingConstants.nameType;
import static com.hedera.services.parsing.ParsingConstants.notSpecifiedType;
import static com.hedera.services.parsing.ParsingConstants.ownerOfType;
import static com.hedera.services.parsing.ParsingConstants.symbolType;
import static com.hedera.services.parsing.ParsingConstants.tokenUriType;
import static com.hedera.services.parsing.ParsingConstants.totalSupplyType;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.bdd.suites.utils.contracts.ContractCallResult;
import com.hedera.services.parsing.ParsingConstants.FunctionType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;

public class HTSPrecompileResult implements ContractCallResult {
    private HTSPrecompileResult() {}

    public static HTSPrecompileResult htsPrecompileResult() {
        return new HTSPrecompileResult();
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
        this.tupleType =
                switch (functionType) {
                    case MINT -> mintReturnType;
                    case BURN -> burnReturnType;
                    case TOTAL_SUPPLY -> totalSupplyType;
                    case DECIMALS -> decimalsType;
                    case BALANCE -> balanceOfType;
                    case OWNER -> ownerOfType;
                    case GET_APPROVED -> getApprovedType;
                    case NAME -> nameType;
                    case SYMBOL -> symbolType;
                    case TOKEN_URI -> tokenUriType;
                    case ERC_TRANSFER -> ercTransferType;
                    case ALLOWANCE -> allowanceOfType;
                    case IS_APPROVED_FOR_ALL -> isApprovedForAllType;
                    case GET_TOKEN_INFO -> getTokenInfoTypeReplacedAddress;
                    case GET_FUNGIBLE_TOKEN_INFO -> getFungibleTokenInfoTypeReplacedAddress;
                    case GET_NON_FUNGIBLE_TOKEN_INFO -> getNonFungibleTokenInfoTypeReplacedAddress;
                    default -> notSpecifiedType;
                };

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
        Tuple result =
                switch (functionType) {
                    case MINT -> Tuple.of(
                            status.getNumber(), BigInteger.valueOf(totalSupply), serialNumbers);
                    case BURN -> Tuple.of(status.getNumber(), BigInteger.valueOf(totalSupply));
                    case TOTAL_SUPPLY -> Tuple.of(BigInteger.valueOf(totalSupply));
                    case DECIMALS -> Tuple.of(decimals);
                    case BALANCE -> Tuple.of(BigInteger.valueOf(balance));
                    case NAME -> Tuple.of(name);
                    case SYMBOL -> Tuple.of(symbol);
                    case TOKEN_URI -> Tuple.of(metadata);
                    case ERC_TRANSFER -> Tuple.of(ercFungibleTransferStatus);
                    case IS_APPROVED_FOR_ALL -> Tuple.of(isApprovedForAllStatus);
                    case ALLOWANCE -> Tuple.of(BigInteger.valueOf(allowance));
                    case GET_TOKEN_INFO -> getTupleForGetTokenInfo();
                    case GET_FUNGIBLE_TOKEN_INFO -> getTupleForGetFungibleTokenInfo();
                    case GET_NON_FUNGIBLE_TOKEN_INFO -> getTupleForGetNonFungibleTokenInfo();
                    default -> Tuple.of(status.getNumber());
                };

        if (FunctionType.OWNER.equals(functionType)) {

            return Bytes.wrap(expandByteArrayTo32Length(owner));
        } else if (FunctionType.GET_APPROVED.equals(functionType)) {

            return Bytes.wrap(expandByteArrayTo32Length(spender));
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
                        expandByteArrayTo32Length(owner),
                        creationTime,
                        metadata.getBytes(),
                        expandByteArrayTo32Length(spender)));
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
                                    ? fixedFee.tokenId().toArray()
                                    : new byte[32],
                            fixedFee.useHbarsForPayment(),
                            fixedFee.useCurrentTokenForPayment(),
                            fixedFee.feeCollector() != null
                                    ? fixedFee.feeCollector().toArray()
                                    : new byte[32]);
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
                                    ? fractionalFee.feeCollector().toArray()
                                    : new byte[32]);
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
                                    ? royaltyFee.tokenId().toArray()
                                    : new byte[32],
                            royaltyFee.useHbarsForPayment(),
                            royaltyFee.feeCollector() != null
                                    ? royaltyFee.feeCollector().toArray()
                                    : new byte[32]);
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
                        expiry.autoRenewAccount().toArray(),
                        expiry.autoRenewPeriod());

        return Tuple.of(
                hederaToken.name(),
                hederaToken.symbol(),
                hederaToken.treasury().toArray(),
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
                                    ? keyValue.contractId().toArray()
                                    : new byte[32],
                            keyValue.ed25519(),
                            keyValue.ECDSA_secp256k1(),
                            keyValue.delegatableContractId() != null
                                    ? keyValue.delegatableContractId().toArray()
                                    : new byte[32]);
            tokenKeysTuples[i] = (Tuple.of(BigInteger.valueOf(key.keyType()), keyValueTuple));
        }

        return tokenKeysTuples;
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
