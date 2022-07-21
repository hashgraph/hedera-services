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

import static com.hedera.services.store.contracts.precompile.codec.EncodingFacade.FunctionType.MINT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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

@Singleton
public class EncodingFacade {
    public static final Bytes SUCCESS_RESULT = resultFrom(SUCCESS);
    private static final long[] NO_MINTED_SERIAL_NUMBERS = new long[0];
    private static final String STRING_RETURN_TYPE = "(string)";
    public static final String UINT256_RETURN_TYPE = "(uint256)";
    public static final String BOOL_RETURN_TYPE = "(bool)";
    private static final TupleType mintReturnType = TupleType.parse("(int32,uint64,int64[])");
    private static final TupleType burnReturnType = TupleType.parse("(int32,uint64)");
    private static final TupleType createReturnType = TupleType.parse("(int32,address)");
    private static final TupleType totalSupplyType = TupleType.parse(UINT256_RETURN_TYPE);
    private static final TupleType balanceOfType = TupleType.parse(UINT256_RETURN_TYPE);
    private static final TupleType allowanceOfType = TupleType.parse(UINT256_RETURN_TYPE);
    private static final TupleType approveOfType = TupleType.parse(BOOL_RETURN_TYPE);
    private static final TupleType decimalsType = TupleType.parse("(uint8)");
    private static final TupleType ownerOfType = TupleType.parse("(address)");
    private static final TupleType getApprovedType = TupleType.parse("(address)");
    private static final TupleType nameType = TupleType.parse(STRING_RETURN_TYPE);
    private static final TupleType symbolType = TupleType.parse(STRING_RETURN_TYPE);
    private static final TupleType tokenUriType = TupleType.parse(STRING_RETURN_TYPE);
    private static final TupleType ercTransferType = TupleType.parse(BOOL_RETURN_TYPE);
    private static final TupleType isApprovedForAllType = TupleType.parse(BOOL_RETURN_TYPE);
    private static final TupleType getTokenDefaultFreezeStatusType = TupleType.parse("(int32,bool)");
    private static final TupleType getTokenDefaultKycStatusType = TupleType.parse("(int32,bool)");

    @Inject
    public EncodingFacade() {
        /* For Dagger2 */
    }

    public static Bytes resultFrom(final ResponseCodeEnum status) {
        return UInt256.valueOf(status.getNumber());
    }

    public Bytes encodeTokenUri(final String tokenUri) {
        return functionResultBuilder()
                .forFunction(FunctionType.TOKEN_URI)
                .withTokenUri(tokenUri)
                .build();
    }

    public Bytes encodeSymbol(final String symbol) {
        return functionResultBuilder().forFunction(FunctionType.SYMBOL).withSymbol(symbol).build();
    }

    public Bytes encodeName(final String name) {
        return functionResultBuilder().forFunction(FunctionType.NAME).withName(name).build();
    }

    public Bytes encodeOwner(final Address address) {
        return functionResultBuilder().forFunction(FunctionType.OWNER).withOwner(address).build();
    }

    public Bytes encodeGetApproved(final Address approved) {
        return functionResultBuilder()
                .forFunction(FunctionType.GET_APPROVED)
                .withApproved(approved)
                .build();
    }

    public Bytes encodeBalance(final long balance) {
        return functionResultBuilder()
                .forFunction(FunctionType.BALANCE)
                .withBalance(balance)
                .build();
    }

    public Bytes encodeAllowance(final long allowance) {
        return functionResultBuilder()
                .forFunction(FunctionType.ALLOWANCE)
                .withAllowance(allowance)
                .build();
    }

    public Bytes encodeApprove(final boolean approve) {
        return functionResultBuilder()
                .forFunction(FunctionType.APPROVE)
                .withApprove(approve)
                .build();
    }

    public Bytes encodeDecimals(final int decimals) {
        return functionResultBuilder()
                .forFunction(FunctionType.DECIMALS)
                .withDecimals(decimals)
                .build();
    }

    public Bytes encodeTotalSupply(final long totalSupply) {
        return functionResultBuilder()
                .forFunction(FunctionType.TOTAL_SUPPLY)
                .withTotalSupply(totalSupply)
                .build();
    }

    public Bytes encodeMintSuccess(final long totalSupply, final long[] serialNumbers) {
        return functionResultBuilder()
                .forFunction(MINT)
                .withStatus(SUCCESS.getNumber())
                .withTotalSupply(totalSupply)
                .withSerialNumbers(serialNumbers != null ? serialNumbers : NO_MINTED_SERIAL_NUMBERS)
                .build();
    }

    public Bytes encodeMintFailure(final ResponseCodeEnum status) {
        return functionResultBuilder()
                .forFunction(MINT)
                .withStatus(status.getNumber())
                .withTotalSupply(0L)
                .withSerialNumbers(NO_MINTED_SERIAL_NUMBERS)
                .build();
    }

    public Bytes encodeBurnSuccess(final long totalSupply) {
        return functionResultBuilder()
                .forFunction(FunctionType.BURN)
                .withStatus(SUCCESS.getNumber())
                .withTotalSupply(totalSupply)
                .build();
    }

    public Bytes encodeBurnFailure(final ResponseCodeEnum status) {
        return functionResultBuilder()
                .forFunction(FunctionType.BURN)
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
                .forFunction(FunctionType.CREATE)
                .withStatus(SUCCESS.getNumber())
                .withNewTokenAddress(newTokenAddress)
                .build();
    }

    public Bytes encodeCreateFailure(final ResponseCodeEnum status) {
        return functionResultBuilder()
                .forFunction(FunctionType.CREATE)
                .withStatus(status.getNumber())
                .withNewTokenAddress(Address.ZERO)
                .build();
    }

    public Bytes encodeIsApprovedForAll(final boolean isApprovedForAllStatus) {
        return functionResultBuilder()
                .forFunction(FunctionType.IS_APPROVED_FOR_ALL)
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
            .withGetTokenDefaultKycStatus(defaultKycStatus)
            .build();
    }

    protected enum FunctionType {
        CREATE,
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
        ALLOWANCE,
        APPROVE,
        GET_APPROVED,
        IS_APPROVED_FOR_ALL,
        GET_TOKEN_DEFAULT_FREEZE_STATUS,
        GET_TOKEN_DEFAULT_KYC_STATUS
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

        private FunctionResultBuilder forFunction(final FunctionType functionType) {
            this.tupleType =
                    switch (functionType) {
                        case CREATE -> createReturnType;
                        case MINT -> mintReturnType;
                        case BURN -> burnReturnType;
                        case TOTAL_SUPPLY -> totalSupplyType;
                        case DECIMALS -> decimalsType;
                        case BALANCE -> balanceOfType;
                        case OWNER -> ownerOfType;
                        case NAME -> nameType;
                        case SYMBOL -> symbolType;
                        case TOKEN_URI -> tokenUriType;
                        case ERC_TRANSFER -> ercTransferType;
                        case ALLOWANCE -> allowanceOfType;
                        case APPROVE -> approveOfType;
                        case GET_APPROVED -> getApprovedType;
                        case IS_APPROVED_FOR_ALL -> isApprovedForAllType;
                        case GET_TOKEN_DEFAULT_FREEZE_STATUS -> getTokenDefaultFreezeStatusType;
                        case GET_TOKEN_DEFAULT_KYC_STATUS -> getTokenDefaultKycStatusType;
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

        private FunctionResultBuilder withGetTokenDefaultFreezeStatus(final boolean tokenDefaultFreezeStatus) {
            this.tokenDefaultFreezeStatus = tokenDefaultFreezeStatus;
            return this;
        }

        private FunctionResultBuilder withGetTokenDefaultKycStatus(final boolean tokenDefaultKycStatus) {
            this.tokenDefaultKycStatus = tokenDefaultKycStatus;
            return this;
        }

        private Bytes build() {
            final var result =
                    switch (functionType) {
                        case CREATE -> Tuple.of(
                                status, convertBesuAddressToHeadlongAddress(newTokenAddress));
                        case MINT -> Tuple.of(
                                status, BigInteger.valueOf(totalSupply), serialNumbers);
                        case BURN -> Tuple.of(status, BigInteger.valueOf(totalSupply));
                        case TOTAL_SUPPLY -> Tuple.of(BigInteger.valueOf(totalSupply));
                        case DECIMALS -> Tuple.of(decimals);
                        case BALANCE -> Tuple.of(BigInteger.valueOf(balance));
                        case OWNER -> Tuple.of(convertBesuAddressToHeadlongAddress(owner));
                        case NAME -> Tuple.of(name);
                        case SYMBOL -> Tuple.of(symbol);
                        case TOKEN_URI -> Tuple.of(metadata);
                        case ERC_TRANSFER -> Tuple.of(ercFungibleTransferStatus);
                        case ALLOWANCE -> Tuple.of(BigInteger.valueOf(allowance));
                        case APPROVE -> Tuple.of(approve);
                        case GET_APPROVED -> Tuple.of(
                                convertBesuAddressToHeadlongAddress(approved));
                        case IS_APPROVED_FOR_ALL -> Tuple.of(isApprovedForAllStatus);
                        case GET_TOKEN_DEFAULT_FREEZE_STATUS -> Tuple.of(status, tokenDefaultFreezeStatus);
                        case GET_TOKEN_DEFAULT_KYC_STATUS -> Tuple.of(status, tokenDefaultKycStatus);
                    };

            return Bytes.wrap(tupleType.encode(result).array());
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
            final Address address) {
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(
                        address.toUnsignedBigInteger()));
    }
}
