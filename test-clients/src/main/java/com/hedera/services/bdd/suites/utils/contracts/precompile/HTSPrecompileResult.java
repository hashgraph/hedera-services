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

public class HTSPrecompileResult implements ContractCallResult {
    private HTSPrecompileResult() {}

    public static final String INT_BOOL_PAIR_RETURN_TYPE = "(int32,bool)";

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
    private static final TupleType hapiAllowanceOfType = TupleType.parse("(int32,uint256)");
    private static final TupleType hapiGetApprovedType = TupleType.parse("(int32,bytes32)");
    private static final TupleType hapiIsApprovedForAllType = TupleType.parse(INT_BOOL_PAIR_RETURN_TYPE);
    private static final TupleType getTokenDefaultFreezeStatusType = TupleType.parse(INT_BOOL_PAIR_RETURN_TYPE);
    private static final TupleType getTokenDefaultKycStatusType = TupleType.parse(INT_BOOL_PAIR_RETURN_TYPE);

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
        HAPI_ALLOWANCE,
        HAPI_IS_APPROVED_FOR_ALL,
        HAPI_GET_APPROVED,
        GET_TOKEN_DEFAULT_FREEZE_STATUS,
        GET_TOKEN_DEFAULT_KYC_STATUS
    }

    private FunctionType functionType = FunctionType.NOT_SPECIFIED;
    private TupleType tupleType = notSpecifiedType;
    private ResponseCodeEnum status;
    private long totalSupply;
    private long[] serialNumbers;
    private int decimals;
    private byte[] owner;
    private byte[] approved;
    private String name;
    private String symbol;
    private String metadata;
    private long balance;
    private long allowance;
    private boolean ercFungibleTransferStatus;
    private boolean isApprovedForAllStatus;
    private boolean tokenDefaultFreezeStatus;
    private boolean tokenDefaultKycStatus;

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
            case HAPI_GET_APPROVED -> tupleType = hapiGetApprovedType;
            case HAPI_ALLOWANCE -> tupleType = hapiAllowanceOfType;
            case HAPI_IS_APPROVED_FOR_ALL -> tupleType = hapiIsApprovedForAllType;
            case GET_TOKEN_DEFAULT_FREEZE_STATUS -> tupleType = getTokenDefaultFreezeStatusType;
            case GET_TOKEN_DEFAULT_KYC_STATUS -> tupleType = getTokenDefaultKycStatusType;
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

    public HTSPrecompileResult withApproved(final byte[] approved) {
        this.approved = approved;
        return this;
    }

    public HTSPrecompileResult withApproved(final ResponseCodeEnum status, final byte[] approved) {
        this.status = status;
        this.approved = approved;
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

    public HTSPrecompileResult withIsApprovedForAll(
            final ResponseCodeEnum status, final boolean isApprovedForAllStatus) {
        this.status = status;
        this.isApprovedForAllStatus = isApprovedForAllStatus;
        return this;
    }

    public HTSPrecompileResult withTokenDefaultFreezeStatus(final boolean tokenDefaultFreezeStatus) {
        this.tokenDefaultFreezeStatus = tokenDefaultFreezeStatus;
        return this;
    }

    public HTSPrecompileResult withTokenDefaultKycStatus(final boolean tokenDefaultKycStatus) {
        this.tokenDefaultKycStatus = tokenDefaultKycStatus;
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
                return Bytes.wrap(expandByteArrayTo32Length(approved));
            }
            case NAME -> result = Tuple.of(name);
            case SYMBOL -> result = Tuple.of(symbol);
            case TOKEN_URI -> result = Tuple.of(metadata);
            case ERC_TRANSFER -> result = Tuple.of(ercFungibleTransferStatus);
            case IS_APPROVED_FOR_ALL -> result = Tuple.of(isApprovedForAllStatus);
            case ALLOWANCE -> result = Tuple.of(BigInteger.valueOf(allowance));
            case HAPI_IS_APPROVED_FOR_ALL -> result =
                    Tuple.of(status.getNumber(), isApprovedForAllStatus);
            case HAPI_ALLOWANCE -> result =
                    Tuple.of(status.getNumber(), BigInteger.valueOf(allowance));
            case HAPI_GET_APPROVED -> result =
                    Tuple.of(status.getNumber(), expandByteArrayTo32Length(approved));
            case GET_TOKEN_DEFAULT_FREEZE_STATUS -> result = Tuple.of(status.getNumber(), tokenDefaultFreezeStatus);
            case GET_TOKEN_DEFAULT_KYC_STATUS -> result = Tuple.of(status.getNumber(), tokenDefaultKycStatus);
            default -> result = Tuple.of(status.getNumber());
        }
        return Bytes.wrap(tupleType.encode(result).array());
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
