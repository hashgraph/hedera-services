/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.pbj;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.Utils.asHederaKeyUnchecked;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FeeComponents;
import com.hedera.hapi.node.base.FeeData;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.network.NetworkGetExecutionTimeQuery;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleInfo;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshot;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.security.InvalidKeyException;
import java.util.Optional;

public final class PbjConverter {
    public static @NonNull AccountID toPbj(@NonNull com.hederahashgraph.api.proto.java.AccountID accountID) {
        requireNonNull(accountID);
        final var builder =
                AccountID.newBuilder().shardNum(accountID.getShardNum()).realmNum(accountID.getRealmNum());
        if (accountID.getAccountCase() == com.hederahashgraph.api.proto.java.AccountID.AccountCase.ALIAS) {
            builder.alias(Bytes.wrap(accountID.getAlias().toByteArray()));
        } else {
            builder.accountNum(accountID.getAccountNum());
        }
        return builder.build();
    }

    public static @NonNull TokenID toPbj(@NonNull com.hederahashgraph.api.proto.java.TokenID tokenID) {
        requireNonNull(tokenID);
        return TokenID.newBuilder()
                .shardNum(tokenID.getShardNum())
                .realmNum(tokenID.getRealmNum())
                .tokenNum(tokenID.getTokenNum())
                .build();
    }

    public static @NonNull TransactionBody toPbj(@NonNull com.hederahashgraph.api.proto.java.TransactionBody txBody) {
        requireNonNull(txBody);
        try {
            final var bytes = txBody.toByteArray();
            return TransactionBody.PROTOBUF.parse(BufferedData.wrap(bytes));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull Key toPbj(@NonNull com.hederahashgraph.api.proto.java.Key keyValue) {
        requireNonNull(keyValue);
        try {
            final var bytes = keyValue.toByteArray();
            return Key.PROTOBUF.parse(BufferedData.wrap(bytes));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull com.hederahashgraph.api.proto.java.FeeData fromPbj(@NonNull FeeData feeData) {
        requireNonNull(feeData);
        return com.hederahashgraph.api.proto.java.FeeData.newBuilder()
                .setNodedata(fromPbj(feeData.nodedata()))
                .setNetworkdata(fromPbj(feeData.networkdata()))
                .setServicedata(fromPbj(feeData.servicedata()))
                .setSubTypeValue(feeData.subType().protoOrdinal())
                .build();
    }

    public static @NonNull com.hederahashgraph.api.proto.java.FeeComponents fromPbj(
            @NonNull FeeComponents feeComponents) {
        requireNonNull(feeComponents);
        return com.hederahashgraph.api.proto.java.FeeComponents.newBuilder()
                .setMin(feeComponents.min())
                .setMax(feeComponents.max())
                .setConstant(feeComponents.constant())
                .setBpt(feeComponents.bpt())
                .setVpt(feeComponents.vpt())
                .setRbh(feeComponents.rbh())
                .setSbh(feeComponents.sbh())
                .setGas(feeComponents.gas())
                .setTv(feeComponents.tv())
                .setBpr(feeComponents.bpr())
                .setSbpr(feeComponents.sbpr())
                .build();
    }

    public static @NonNull com.hederahashgraph.api.proto.java.ExchangeRate fromPbj(@NonNull ExchangeRate exchangeRate) {
        return com.hederahashgraph.api.proto.java.ExchangeRate.newBuilder()
                .setCentEquiv(exchangeRate.centEquiv())
                .setHbarEquiv(exchangeRate.hbarEquiv())
                .build();
    }

    public static @NonNull com.hederahashgraph.api.proto.java.Key fromPbj(@NonNull Key keyValue) {
        requireNonNull(keyValue);
        try {
            final var bytes = asBytes(Key.PROTOBUF, keyValue);
            return com.hederahashgraph.api.proto.java.Key.parseFrom(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull com.hederahashgraph.api.proto.java.KeyList fromPbj(@NonNull KeyList keyValue) {
        requireNonNull(keyValue);
        try {
            final var bytes = asBytes(KeyList.PROTOBUF, keyValue);
            return com.hederahashgraph.api.proto.java.KeyList.parseFrom(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull com.hederahashgraph.api.proto.java.ScheduleInfo fromPbj(@NonNull ScheduleInfo pbjValue) {
        requireNonNull(pbjValue);
        try {
            final var bytes = asBytes(ScheduleInfo.PROTOBUF, pbjValue);
            return com.hederahashgraph.api.proto.java.ScheduleInfo.parseFrom(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull com.hederahashgraph.api.proto.java.AccountID fromPbj(@NonNull AccountID accountID) {
        requireNonNull(accountID);
        final var builder = com.hederahashgraph.api.proto.java.AccountID.newBuilder()
                .setShardNum(accountID.shardNum())
                .setRealmNum(accountID.realmNum());

        final var account = accountID.account();
        switch (account.kind()) {
            case ACCOUNT_NUM -> builder.setAccountNum(account.as());
            case ALIAS -> builder.setAlias(fromPbj((Bytes) account.as()));
            case UNSET -> throw new RuntimeException("Invalid account ID, no account type!");
        }

        return builder.build();
    }

    public static @NonNull com.hederahashgraph.api.proto.java.Transaction fromPbj(@NonNull Transaction tx) {
        requireNonNull(tx);
        try {
            final var bytes = asBytes(Transaction.PROTOBUF, tx);
            return com.hederahashgraph.api.proto.java.Transaction.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull com.hederahashgraph.api.proto.java.TransactionBody fromPbj(@NonNull TransactionBody tx) {
        requireNonNull(tx);
        try {
            final var bytes = asBytes(TransactionBody.PROTOBUF, tx);
            return com.hederahashgraph.api.proto.java.TransactionBody.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull com.hederahashgraph.api.proto.java.TransactionRecord fromPbj(@NonNull TransactionRecord tx) {
        requireNonNull(tx);
        try {
            final var bytes = asBytes(TransactionRecord.PROTOBUF, tx);
            return com.hederahashgraph.api.proto.java.TransactionRecord.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull com.hederahashgraph.api.proto.java.SubType fromPbj(@NonNull SubType subType) {
        requireNonNull(subType);
        return com.hederahashgraph.api.proto.java.SubType.valueOf(subType.name());
    }

    public static @NonNull com.hederahashgraph.api.proto.java.AccountAmount fromPbj(@NonNull AccountAmount a) {
        requireNonNull(a);
        try {
            final var bytes = asBytes(AccountAmount.PROTOBUF, a);
            return com.hederahashgraph.api.proto.java.AccountAmount.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull ByteString fromPbj(@NonNull Bytes bytes) {
        requireNonNull(bytes);
        final byte[] data = new byte[Math.toIntExact(bytes.length())];
        bytes.getBytes(0, data);
        return ByteString.copyFrom(data);
    }

    public static @NonNull com.hederahashgraph.api.proto.java.Query fromPbj(@NonNull Query query) {
        requireNonNull(query);
        try {
            final var bytes = asBytes(Query.PROTOBUF, query);
            return com.hederahashgraph.api.proto.java.Query.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull SubType toPbj(@NonNull com.hederahashgraph.api.proto.java.SubType subType) {
        requireNonNull(subType);
        return switch (subType) {
            case DEFAULT -> SubType.DEFAULT;
            case TOKEN_FUNGIBLE_COMMON -> SubType.TOKEN_FUNGIBLE_COMMON;
            case TOKEN_NON_FUNGIBLE_UNIQUE -> SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
            case TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES -> SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
            case TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES -> SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
            case SCHEDULE_CREATE_CONTRACT_CALL -> SubType.SCHEDULE_CREATE_CONTRACT_CALL;
            case UNRECOGNIZED -> throw new IllegalArgumentException("Unknown subType UNRECOGNIZED");
        };
    }

    public static @NonNull HederaFunctionality toPbj(
            @NonNull com.hederahashgraph.api.proto.java.HederaFunctionality function) {
        requireNonNull(function);
        return switch (function) {
            case Freeze -> HederaFunctionality.FREEZE;
            case GetByKey -> HederaFunctionality.GET_BY_KEY;
            case ConsensusCreateTopic -> HederaFunctionality.CONSENSUS_CREATE_TOPIC;
            case ConsensusDeleteTopic -> HederaFunctionality.CONSENSUS_DELETE_TOPIC;
            case ConsensusGetTopicInfo -> HederaFunctionality.CONSENSUS_GET_TOPIC_INFO;
            case ConsensusSubmitMessage -> HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE;
            case ConsensusUpdateTopic -> HederaFunctionality.CONSENSUS_UPDATE_TOPIC;
            case ContractAutoRenew -> HederaFunctionality.CONTRACT_AUTO_RENEW;
            case ContractCall -> HederaFunctionality.CONTRACT_CALL;
            case ContractCallLocal -> HederaFunctionality.CONTRACT_CALL_LOCAL;
            case ContractCreate -> HederaFunctionality.CONTRACT_CREATE;
            case ContractDelete -> HederaFunctionality.CONTRACT_DELETE;
            case ContractGetBytecode -> HederaFunctionality.CONTRACT_GET_BYTECODE;
            case ContractGetInfo -> HederaFunctionality.CONTRACT_GET_INFO;
            case ContractGetRecords -> HederaFunctionality.CONTRACT_GET_RECORDS;
            case ContractUpdate -> HederaFunctionality.CONTRACT_UPDATE;
            case CreateTransactionRecord -> HederaFunctionality.CREATE_TRANSACTION_RECORD;
            case CryptoAccountAutoRenew -> HederaFunctionality.CRYPTO_ACCOUNT_AUTO_RENEW;
            case CryptoAddLiveHash -> HederaFunctionality.CRYPTO_ADD_LIVE_HASH;
            case CryptoApproveAllowance -> HederaFunctionality.CRYPTO_APPROVE_ALLOWANCE;
            case CryptoCreate -> HederaFunctionality.CRYPTO_CREATE;
            case CryptoDelete -> HederaFunctionality.CRYPTO_DELETE;
            case CryptoDeleteAllowance -> HederaFunctionality.CRYPTO_DELETE_ALLOWANCE;
            case CryptoDeleteLiveHash -> HederaFunctionality.CRYPTO_DELETE_LIVE_HASH;
            case CryptoGetAccountBalance -> HederaFunctionality.CRYPTO_GET_ACCOUNT_BALANCE;
            case CryptoGetAccountRecords -> HederaFunctionality.CRYPTO_GET_ACCOUNT_RECORDS;
            case CryptoGetInfo -> HederaFunctionality.CRYPTO_GET_INFO;
            case CryptoGetLiveHash -> HederaFunctionality.CRYPTO_GET_LIVE_HASH;
            case CryptoGetStakers -> HederaFunctionality.CRYPTO_GET_STAKERS;
            case CryptoTransfer -> HederaFunctionality.CRYPTO_TRANSFER;
            case CryptoUpdate -> HederaFunctionality.CRYPTO_UPDATE;
            case EthereumTransaction -> HederaFunctionality.ETHEREUM_TRANSACTION;
            case FileAppend -> HederaFunctionality.FILE_APPEND;
            case FileCreate -> HederaFunctionality.FILE_CREATE;
            case FileDelete -> HederaFunctionality.FILE_DELETE;
            case FileGetContents -> HederaFunctionality.FILE_GET_CONTENTS;
            case FileGetInfo -> HederaFunctionality.FILE_GET_INFO;
            case FileUpdate -> HederaFunctionality.FILE_UPDATE;
            case GetAccountDetails -> HederaFunctionality.GET_ACCOUNT_DETAILS;
            case GetBySolidityID -> HederaFunctionality.GET_BY_SOLIDITY_ID;
            case GetVersionInfo -> HederaFunctionality.GET_VERSION_INFO;
            case NetworkGetExecutionTime -> HederaFunctionality.NETWORK_GET_EXECUTION_TIME;
            case NONE -> HederaFunctionality.NONE;
            case NodeStakeUpdate -> HederaFunctionality.NODE_STAKE_UPDATE;
            case ScheduleCreate -> HederaFunctionality.SCHEDULE_CREATE;
            case ScheduleDelete -> HederaFunctionality.SCHEDULE_DELETE;
            case ScheduleGetInfo -> HederaFunctionality.SCHEDULE_GET_INFO;
            case ScheduleSign -> HederaFunctionality.SCHEDULE_SIGN;
            case SystemDelete -> HederaFunctionality.SYSTEM_DELETE;
            case SystemUndelete -> HederaFunctionality.SYSTEM_UNDELETE;
            case TokenAccountWipe -> HederaFunctionality.TOKEN_ACCOUNT_WIPE;
            case TokenAssociateToAccount -> HederaFunctionality.TOKEN_ASSOCIATE_TO_ACCOUNT;
            case TokenBurn -> HederaFunctionality.TOKEN_BURN;
            case TokenCreate -> HederaFunctionality.TOKEN_CREATE;
            case TokenDelete -> HederaFunctionality.TOKEN_DELETE;
            case TokenDissociateFromAccount -> HederaFunctionality.TOKEN_DISSOCIATE_FROM_ACCOUNT;
            case TokenFeeScheduleUpdate -> HederaFunctionality.TOKEN_FEE_SCHEDULE_UPDATE;
            case TokenFreezeAccount -> HederaFunctionality.TOKEN_FREEZE_ACCOUNT;
            case TokenGetAccountNftInfos -> HederaFunctionality.TOKEN_GET_ACCOUNT_NFT_INFOS;
            case TokenGetInfo -> HederaFunctionality.TOKEN_GET_INFO;
            case TokenGetNftInfo -> HederaFunctionality.TOKEN_GET_NFT_INFO;
            case TokenGetNftInfos -> HederaFunctionality.TOKEN_GET_NFT_INFOS;
            case TokenGrantKycToAccount -> HederaFunctionality.TOKEN_GRANT_KYC_TO_ACCOUNT;
            case TokenMint -> HederaFunctionality.TOKEN_MINT;
            case TokenPause -> HederaFunctionality.TOKEN_PAUSE;
            case TokenRevokeKycFromAccount -> HederaFunctionality.TOKEN_REVOKE_KYC_FROM_ACCOUNT;
            case TokenUnfreezeAccount -> HederaFunctionality.TOKEN_UNFREEZE_ACCOUNT;
            case TokenUnpause -> HederaFunctionality.TOKEN_UNPAUSE;
            case TokenUpdate -> HederaFunctionality.TOKEN_UPDATE;
            case TokenUpdateNfts -> HederaFunctionality.TOKEN_UPDATE_NFTS;
            case TransactionGetReceipt -> HederaFunctionality.TRANSACTION_GET_RECEIPT;
            case TransactionGetRecord -> HederaFunctionality.TRANSACTION_GET_RECORD;
            case TransactionGetFastRecord -> HederaFunctionality.TRANSACTION_GET_FAST_RECORD;
            case UncheckedSubmit -> HederaFunctionality.UNCHECKED_SUBMIT;
            case UtilPrng -> HederaFunctionality.UTIL_PRNG;
            case UNRECOGNIZED -> throw new RuntimeException("Unknown function UNRECOGNIZED");
        };
    }

    public static @NonNull com.hederahashgraph.api.proto.java.HederaFunctionality fromPbj(
            @NonNull HederaFunctionality function) {
        requireNonNull(function);
        return switch (function) {
            case FREEZE -> com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
            case GET_BY_KEY -> com.hederahashgraph.api.proto.java.HederaFunctionality.GetByKey;
            case CONSENSUS_CREATE_TOPIC -> com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
            case CONSENSUS_DELETE_TOPIC -> com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
            case CONSENSUS_GET_TOPIC_INFO -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .ConsensusGetTopicInfo;
            case CONSENSUS_SUBMIT_MESSAGE -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .ConsensusSubmitMessage;
            case CONSENSUS_UPDATE_TOPIC -> com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
            case CONTRACT_AUTO_RENEW -> com.hederahashgraph.api.proto.java.HederaFunctionality.ContractAutoRenew;
            case CONTRACT_CALL -> com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
            case CONTRACT_CALL_LOCAL -> com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
            case CONTRACT_CREATE -> com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
            case CONTRACT_DELETE -> com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
            case CONTRACT_GET_BYTECODE -> com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetBytecode;
            case CONTRACT_GET_INFO -> com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
            case CONTRACT_GET_RECORDS -> com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
            case CONTRACT_UPDATE -> com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
            case CREATE_TRANSACTION_RECORD -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .CreateTransactionRecord;
            case CRYPTO_ACCOUNT_AUTO_RENEW -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .CryptoAccountAutoRenew;
            case CRYPTO_ADD_LIVE_HASH -> com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAddLiveHash;
            case CRYPTO_APPROVE_ALLOWANCE -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .CryptoApproveAllowance;
            case CRYPTO_CREATE -> com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
            case CRYPTO_DELETE -> com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
            case CRYPTO_DELETE_ALLOWANCE -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .CryptoDeleteAllowance;
            case CRYPTO_DELETE_LIVE_HASH -> com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteLiveHash;
            case CRYPTO_GET_ACCOUNT_BALANCE -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .CryptoGetAccountBalance;
            case CRYPTO_GET_ACCOUNT_RECORDS -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .CryptoGetAccountRecords;
            case CRYPTO_GET_INFO -> com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
            case CRYPTO_GET_LIVE_HASH -> com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetLiveHash;
            case CRYPTO_GET_STAKERS -> com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetStakers;
            case CRYPTO_TRANSFER -> com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
            case CRYPTO_UPDATE -> com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
            case ETHEREUM_TRANSACTION -> com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
            case FILE_APPEND -> com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
            case FILE_CREATE -> com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
            case FILE_DELETE -> com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
            case FILE_GET_CONTENTS -> com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetContents;
            case FILE_GET_INFO -> com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
            case FILE_UPDATE -> com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
            case GET_ACCOUNT_DETAILS -> com.hederahashgraph.api.proto.java.HederaFunctionality.GetAccountDetails;
            case GET_BY_SOLIDITY_ID -> com.hederahashgraph.api.proto.java.HederaFunctionality.GetBySolidityID;
            case GET_VERSION_INFO -> com.hederahashgraph.api.proto.java.HederaFunctionality.GetVersionInfo;
            case NETWORK_GET_EXECUTION_TIME -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .NetworkGetExecutionTime;
            case NONE -> com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;
            case NODE_STAKE_UPDATE -> com.hederahashgraph.api.proto.java.HederaFunctionality.NodeStakeUpdate;
            case SCHEDULE_CREATE -> com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
            case SCHEDULE_DELETE -> com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleDelete;
            case SCHEDULE_GET_INFO -> com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleGetInfo;
            case SCHEDULE_SIGN -> com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
            case SYSTEM_DELETE -> com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
            case SYSTEM_UNDELETE -> com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;
            case TOKEN_ACCOUNT_WIPE -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
            case TOKEN_ASSOCIATE_TO_ACCOUNT -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .TokenAssociateToAccount;
            case TOKEN_BURN -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
            case TOKEN_CREATE -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
            case TOKEN_DELETE -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
            case TOKEN_DISSOCIATE_FROM_ACCOUNT -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .TokenDissociateFromAccount;
            case TOKEN_FEE_SCHEDULE_UPDATE -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .TokenFeeScheduleUpdate;
            case TOKEN_FREEZE_ACCOUNT -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
            case TOKEN_GET_ACCOUNT_NFT_INFOS -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .TokenGetAccountNftInfos;
            case TOKEN_GET_INFO -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
            case TOKEN_GET_NFT_INFO -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfo;
            case TOKEN_GET_NFT_INFOS -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfos;
            case TOKEN_GRANT_KYC_TO_ACCOUNT -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .TokenGrantKycToAccount;
            case TOKEN_MINT -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
            case TOKEN_PAUSE -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
            case TOKEN_REVOKE_KYC_FROM_ACCOUNT -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .TokenRevokeKycFromAccount;
            case TOKEN_UNFREEZE_ACCOUNT -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
            case TOKEN_UNPAUSE -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
            case TOKEN_UPDATE -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
            case TOKEN_UPDATE_NFTS -> com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdateNfts;
            case TRANSACTION_GET_RECEIPT -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .TransactionGetReceipt;
            case TRANSACTION_GET_RECORD -> com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetRecord;
            case TRANSACTION_GET_FAST_RECORD -> com.hederahashgraph.api.proto.java.HederaFunctionality
                    .TransactionGetFastRecord;
            case UNCHECKED_SUBMIT -> com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;
            case UTIL_PRNG -> com.hederahashgraph.api.proto.java.HederaFunctionality.UtilPrng;
        };
    }

    public static @NonNull ResponseCodeEnum toPbj(@NonNull com.hederahashgraph.api.proto.java.ResponseCodeEnum code) {
        return switch (requireNonNull(code)) {
            case OK -> ResponseCodeEnum.OK;
            case INVALID_TRANSACTION -> ResponseCodeEnum.INVALID_TRANSACTION;
            case PAYER_ACCOUNT_NOT_FOUND -> ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
            case INVALID_NODE_ACCOUNT -> ResponseCodeEnum.INVALID_NODE_ACCOUNT;
            case TRANSACTION_EXPIRED -> ResponseCodeEnum.TRANSACTION_EXPIRED;
            case INVALID_TRANSACTION_START -> ResponseCodeEnum.INVALID_TRANSACTION_START;
            case INVALID_TRANSACTION_DURATION -> ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
            case INVALID_SIGNATURE -> ResponseCodeEnum.INVALID_SIGNATURE;
            case MEMO_TOO_LONG -> ResponseCodeEnum.MEMO_TOO_LONG;
            case INSUFFICIENT_TX_FEE -> ResponseCodeEnum.INSUFFICIENT_TX_FEE;
            case INSUFFICIENT_PAYER_BALANCE -> ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
            case DUPLICATE_TRANSACTION -> ResponseCodeEnum.DUPLICATE_TRANSACTION;
            case BUSY -> ResponseCodeEnum.BUSY;
            case NOT_SUPPORTED -> ResponseCodeEnum.NOT_SUPPORTED;
            case INVALID_FILE_ID -> ResponseCodeEnum.INVALID_FILE_ID;
            case INVALID_ACCOUNT_ID -> ResponseCodeEnum.INVALID_ACCOUNT_ID;
            case INVALID_CONTRACT_ID -> ResponseCodeEnum.INVALID_CONTRACT_ID;
            case INVALID_TRANSACTION_ID -> ResponseCodeEnum.INVALID_TRANSACTION_ID;
            case RECEIPT_NOT_FOUND -> ResponseCodeEnum.RECEIPT_NOT_FOUND;
            case RECORD_NOT_FOUND -> ResponseCodeEnum.RECORD_NOT_FOUND;
            case INVALID_SOLIDITY_ID -> ResponseCodeEnum.INVALID_SOLIDITY_ID;
            case UNKNOWN -> ResponseCodeEnum.UNKNOWN;
            case SUCCESS -> ResponseCodeEnum.SUCCESS;
            case FAIL_INVALID -> ResponseCodeEnum.FAIL_INVALID;
            case FAIL_FEE -> ResponseCodeEnum.FAIL_FEE;
            case FAIL_BALANCE -> ResponseCodeEnum.FAIL_BALANCE;
            case KEY_REQUIRED -> ResponseCodeEnum.KEY_REQUIRED;
            case BAD_ENCODING -> ResponseCodeEnum.BAD_ENCODING;
            case INSUFFICIENT_ACCOUNT_BALANCE -> ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
            case INVALID_SOLIDITY_ADDRESS -> ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
            case INSUFFICIENT_GAS -> ResponseCodeEnum.INSUFFICIENT_GAS;
            case CONTRACT_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.CONTRACT_SIZE_LIMIT_EXCEEDED;
            case LOCAL_CALL_MODIFICATION_EXCEPTION -> ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
            case CONTRACT_REVERT_EXECUTED -> ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
            case CONTRACT_EXECUTION_EXCEPTION -> ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
            case INVALID_RECEIVING_NODE_ACCOUNT -> ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
            case MISSING_QUERY_HEADER -> ResponseCodeEnum.MISSING_QUERY_HEADER;
            case ACCOUNT_UPDATE_FAILED -> ResponseCodeEnum.ACCOUNT_UPDATE_FAILED;
            case INVALID_KEY_ENCODING -> ResponseCodeEnum.INVALID_KEY_ENCODING;
            case NULL_SOLIDITY_ADDRESS -> ResponseCodeEnum.NULL_SOLIDITY_ADDRESS;
            case CONTRACT_UPDATE_FAILED -> ResponseCodeEnum.CONTRACT_UPDATE_FAILED;
            case INVALID_QUERY_HEADER -> ResponseCodeEnum.INVALID_QUERY_HEADER;
            case INVALID_FEE_SUBMITTED -> ResponseCodeEnum.INVALID_FEE_SUBMITTED;
            case INVALID_PAYER_SIGNATURE -> ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
            case KEY_NOT_PROVIDED -> ResponseCodeEnum.KEY_NOT_PROVIDED;
            case INVALID_EXPIRATION_TIME -> ResponseCodeEnum.INVALID_EXPIRATION_TIME;
            case NO_WACL_KEY -> ResponseCodeEnum.NO_WACL_KEY;
            case FILE_CONTENT_EMPTY -> ResponseCodeEnum.FILE_CONTENT_EMPTY;
            case INVALID_ACCOUNT_AMOUNTS -> ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
            case EMPTY_TRANSACTION_BODY -> ResponseCodeEnum.EMPTY_TRANSACTION_BODY;
            case INVALID_TRANSACTION_BODY -> ResponseCodeEnum.INVALID_TRANSACTION_BODY;
            case INVALID_SIGNATURE_TYPE_MISMATCHING_KEY -> ResponseCodeEnum.INVALID_SIGNATURE_TYPE_MISMATCHING_KEY;
            case INVALID_SIGNATURE_COUNT_MISMATCHING_KEY -> ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY;
            case EMPTY_LIVE_HASH_BODY -> ResponseCodeEnum.EMPTY_LIVE_HASH_BODY;
            case EMPTY_LIVE_HASH -> ResponseCodeEnum.EMPTY_LIVE_HASH;
            case EMPTY_LIVE_HASH_KEYS -> ResponseCodeEnum.EMPTY_LIVE_HASH_KEYS;
            case INVALID_LIVE_HASH_SIZE -> ResponseCodeEnum.INVALID_LIVE_HASH_SIZE;
            case EMPTY_QUERY_BODY -> ResponseCodeEnum.EMPTY_QUERY_BODY;
            case EMPTY_LIVE_HASH_QUERY -> ResponseCodeEnum.EMPTY_LIVE_HASH_QUERY;
            case LIVE_HASH_NOT_FOUND -> ResponseCodeEnum.LIVE_HASH_NOT_FOUND;
            case ACCOUNT_ID_DOES_NOT_EXIST -> ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
            case LIVE_HASH_ALREADY_EXISTS -> ResponseCodeEnum.LIVE_HASH_ALREADY_EXISTS;
            case INVALID_FILE_WACL -> ResponseCodeEnum.INVALID_FILE_WACL;
            case SERIALIZATION_FAILED -> ResponseCodeEnum.SERIALIZATION_FAILED;
            case TRANSACTION_OVERSIZE -> ResponseCodeEnum.TRANSACTION_OVERSIZE;
            case TRANSACTION_TOO_MANY_LAYERS -> ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS;
            case CONTRACT_DELETED -> ResponseCodeEnum.CONTRACT_DELETED;
            case PLATFORM_NOT_ACTIVE -> ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
            case KEY_PREFIX_MISMATCH -> ResponseCodeEnum.KEY_PREFIX_MISMATCH;
            case PLATFORM_TRANSACTION_NOT_CREATED -> ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
            case INVALID_RENEWAL_PERIOD -> ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
            case INVALID_PAYER_ACCOUNT_ID -> ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
            case ACCOUNT_DELETED -> ResponseCodeEnum.ACCOUNT_DELETED;
            case FILE_DELETED -> ResponseCodeEnum.FILE_DELETED;
            case ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS -> ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
            case SETTING_NEGATIVE_ACCOUNT_BALANCE -> ResponseCodeEnum.SETTING_NEGATIVE_ACCOUNT_BALANCE;
            case OBTAINER_REQUIRED -> ResponseCodeEnum.OBTAINER_REQUIRED;
            case OBTAINER_SAME_CONTRACT_ID -> ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
            case OBTAINER_DOES_NOT_EXIST -> ResponseCodeEnum.OBTAINER_DOES_NOT_EXIST;
            case MODIFYING_IMMUTABLE_CONTRACT -> ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
            case FILE_SYSTEM_EXCEPTION -> ResponseCodeEnum.FILE_SYSTEM_EXCEPTION;
            case AUTORENEW_DURATION_NOT_IN_RANGE -> ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
            case ERROR_DECODING_BYTESTRING -> ResponseCodeEnum.ERROR_DECODING_BYTESTRING;
            case CONTRACT_FILE_EMPTY -> ResponseCodeEnum.CONTRACT_FILE_EMPTY;
            case CONTRACT_BYTECODE_EMPTY -> ResponseCodeEnum.CONTRACT_BYTECODE_EMPTY;
            case INVALID_INITIAL_BALANCE -> ResponseCodeEnum.INVALID_INITIAL_BALANCE;
            case INVALID_RECEIVE_RECORD_THRESHOLD -> ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
            case INVALID_SEND_RECORD_THRESHOLD -> ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
            case ACCOUNT_IS_NOT_GENESIS_ACCOUNT -> ResponseCodeEnum.ACCOUNT_IS_NOT_GENESIS_ACCOUNT;
            case PAYER_ACCOUNT_UNAUTHORIZED -> ResponseCodeEnum.PAYER_ACCOUNT_UNAUTHORIZED;
            case INVALID_FREEZE_TRANSACTION_BODY -> ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
            case FREEZE_TRANSACTION_BODY_NOT_FOUND -> ResponseCodeEnum.FREEZE_TRANSACTION_BODY_NOT_FOUND;
            case TRANSFER_LIST_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
            case RESULT_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
            case NOT_SPECIAL_ACCOUNT -> ResponseCodeEnum.NOT_SPECIAL_ACCOUNT;
            case CONTRACT_NEGATIVE_GAS -> ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
            case CONTRACT_NEGATIVE_VALUE -> ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
            case INVALID_FEE_FILE -> ResponseCodeEnum.INVALID_FEE_FILE;
            case INVALID_EXCHANGE_RATE_FILE -> ResponseCodeEnum.INVALID_EXCHANGE_RATE_FILE;
            case INSUFFICIENT_LOCAL_CALL_GAS -> ResponseCodeEnum.INSUFFICIENT_LOCAL_CALL_GAS;
            case ENTITY_NOT_ALLOWED_TO_DELETE -> ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
            case AUTHORIZATION_FAILED -> ResponseCodeEnum.AUTHORIZATION_FAILED;
            case FILE_UPLOADED_PROTO_INVALID -> ResponseCodeEnum.FILE_UPLOADED_PROTO_INVALID;
            case FILE_UPLOADED_PROTO_NOT_SAVED_TO_DISK -> ResponseCodeEnum.FILE_UPLOADED_PROTO_NOT_SAVED_TO_DISK;
            case FEE_SCHEDULE_FILE_PART_UPLOADED -> ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
            case EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED -> ResponseCodeEnum.EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED;
            case MAX_CONTRACT_STORAGE_EXCEEDED -> ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
            case TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT -> ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;
            case TOTAL_LEDGER_BALANCE_INVALID -> ResponseCodeEnum.TOTAL_LEDGER_BALANCE_INVALID;
            case EXPIRATION_REDUCTION_NOT_ALLOWED -> ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
            case MAX_GAS_LIMIT_EXCEEDED -> ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
            case MAX_FILE_SIZE_EXCEEDED -> ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
            case RECEIVER_SIG_REQUIRED -> ResponseCodeEnum.RECEIVER_SIG_REQUIRED;
            case INVALID_TOPIC_ID -> ResponseCodeEnum.INVALID_TOPIC_ID;
            case INVALID_ADMIN_KEY -> ResponseCodeEnum.INVALID_ADMIN_KEY;
            case INVALID_SUBMIT_KEY -> ResponseCodeEnum.INVALID_SUBMIT_KEY;
            case UNAUTHORIZED -> ResponseCodeEnum.UNAUTHORIZED;
            case INVALID_TOPIC_MESSAGE -> ResponseCodeEnum.INVALID_TOPIC_MESSAGE;
            case INVALID_AUTORENEW_ACCOUNT -> ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
            case AUTORENEW_ACCOUNT_NOT_ALLOWED -> ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
            case TOPIC_EXPIRED -> ResponseCodeEnum.TOPIC_EXPIRED;
            case INVALID_CHUNK_NUMBER -> ResponseCodeEnum.INVALID_CHUNK_NUMBER;
            case INVALID_CHUNK_TRANSACTION_ID -> ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID;
            case ACCOUNT_FROZEN_FOR_TOKEN -> ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
            case TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED -> ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
            case INVALID_TOKEN_ID -> ResponseCodeEnum.INVALID_TOKEN_ID;
            case INVALID_TOKEN_DECIMALS -> ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
            case INVALID_TOKEN_INITIAL_SUPPLY -> ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
            case INVALID_TREASURY_ACCOUNT_FOR_TOKEN -> ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
            case INVALID_TOKEN_SYMBOL -> ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
            case TOKEN_HAS_NO_FREEZE_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
            case TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN -> ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
            case MISSING_TOKEN_SYMBOL -> ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
            case TOKEN_SYMBOL_TOO_LONG -> ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
            case ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN -> ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
            case TOKEN_HAS_NO_KYC_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
            case INSUFFICIENT_TOKEN_BALANCE -> ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
            case TOKEN_WAS_DELETED -> ResponseCodeEnum.TOKEN_WAS_DELETED;
            case TOKEN_HAS_NO_SUPPLY_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
            case TOKEN_HAS_NO_WIPE_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
            case INVALID_TOKEN_MINT_AMOUNT -> ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
            case INVALID_TOKEN_BURN_AMOUNT -> ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
            case TOKEN_NOT_ASSOCIATED_TO_ACCOUNT -> ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
            case CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT -> ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
            case INVALID_KYC_KEY -> ResponseCodeEnum.INVALID_KYC_KEY;
            case INVALID_WIPE_KEY -> ResponseCodeEnum.INVALID_WIPE_KEY;
            case INVALID_FREEZE_KEY -> ResponseCodeEnum.INVALID_FREEZE_KEY;
            case INVALID_SUPPLY_KEY -> ResponseCodeEnum.INVALID_SUPPLY_KEY;
            case MISSING_TOKEN_NAME -> ResponseCodeEnum.MISSING_TOKEN_NAME;
            case TOKEN_NAME_TOO_LONG -> ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
            case INVALID_WIPING_AMOUNT -> ResponseCodeEnum.INVALID_WIPING_AMOUNT;
            case TOKEN_IS_IMMUTABLE -> ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
            case TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT -> ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
            case TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES -> ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
            case ACCOUNT_IS_TREASURY -> ResponseCodeEnum.ACCOUNT_IS_TREASURY;
            case TOKEN_ID_REPEATED_IN_TOKEN_LIST -> ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
            case TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
            case EMPTY_TOKEN_TRANSFER_BODY -> ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_BODY;
            case EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS -> ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
            case INVALID_SCHEDULE_ID -> ResponseCodeEnum.INVALID_SCHEDULE_ID;
            case SCHEDULE_IS_IMMUTABLE -> ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
            case INVALID_SCHEDULE_PAYER_ID -> ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID;
            case INVALID_SCHEDULE_ACCOUNT_ID -> ResponseCodeEnum.INVALID_SCHEDULE_ACCOUNT_ID;
            case NO_NEW_VALID_SIGNATURES -> ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
            case UNRESOLVABLE_REQUIRED_SIGNERS -> ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
            case SCHEDULED_TRANSACTION_NOT_IN_WHITELIST -> ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
            case SOME_SIGNATURES_WERE_INVALID -> ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
            case TRANSACTION_ID_FIELD_NOT_ALLOWED -> ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;
            case IDENTICAL_SCHEDULE_ALREADY_CREATED -> ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
            case INVALID_ZERO_BYTE_IN_STRING -> ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
            case SCHEDULE_ALREADY_DELETED -> ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
            case SCHEDULE_ALREADY_EXECUTED -> ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
            case MESSAGE_SIZE_TOO_LARGE -> ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
            case OPERATION_REPEATED_IN_BUCKET_GROUPS -> ResponseCodeEnum.OPERATION_REPEATED_IN_BUCKET_GROUPS;
            case BUCKET_CAPACITY_OVERFLOW -> ResponseCodeEnum.BUCKET_CAPACITY_OVERFLOW;
            case NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION -> ResponseCodeEnum
                    .NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION;
            case BUCKET_HAS_NO_THROTTLE_GROUPS -> ResponseCodeEnum.BUCKET_HAS_NO_THROTTLE_GROUPS;
            case THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC -> ResponseCodeEnum.THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC;
            case SUCCESS_BUT_MISSING_EXPECTED_OPERATION -> ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
            case UNPARSEABLE_THROTTLE_DEFINITIONS -> ResponseCodeEnum.UNPARSEABLE_THROTTLE_DEFINITIONS;
            case INVALID_THROTTLE_DEFINITIONS -> ResponseCodeEnum.INVALID_THROTTLE_DEFINITIONS;
            case ACCOUNT_EXPIRED_AND_PENDING_REMOVAL -> ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
            case INVALID_TOKEN_MAX_SUPPLY -> ResponseCodeEnum.INVALID_TOKEN_MAX_SUPPLY;
            case INVALID_TOKEN_NFT_SERIAL_NUMBER -> ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
            case INVALID_NFT_ID -> ResponseCodeEnum.INVALID_NFT_ID;
            case METADATA_TOO_LONG -> ResponseCodeEnum.METADATA_TOO_LONG;
            case BATCH_SIZE_LIMIT_EXCEEDED -> ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
            case INVALID_QUERY_RANGE -> ResponseCodeEnum.INVALID_QUERY_RANGE;
            case FRACTION_DIVIDES_BY_ZERO -> ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
            case INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE -> ResponseCodeEnum
                    .INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE;
            case CUSTOM_FEES_LIST_TOO_LONG -> ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
            case INVALID_CUSTOM_FEE_COLLECTOR -> ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
            case INVALID_TOKEN_ID_IN_CUSTOM_FEES -> ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
            case TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR -> ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
            case TOKEN_MAX_SUPPLY_REACHED -> ResponseCodeEnum.TOKEN_MAX_SUPPLY_REACHED;
            case SENDER_DOES_NOT_OWN_NFT_SERIAL_NO -> ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
            case CUSTOM_FEE_NOT_FULLY_SPECIFIED -> ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
            case CUSTOM_FEE_MUST_BE_POSITIVE -> ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
            case TOKEN_HAS_NO_FEE_SCHEDULE_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
            case CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE -> ResponseCodeEnum.CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
            case ROYALTY_FRACTION_CANNOT_EXCEED_ONE -> ResponseCodeEnum.ROYALTY_FRACTION_CANNOT_EXCEED_ONE;
            case FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT -> ResponseCodeEnum
                    .FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT;
            case CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES -> ResponseCodeEnum.CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES;
            case CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON -> ResponseCodeEnum
                    .CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
            case CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON -> ResponseCodeEnum
                    .CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
            case INVALID_CUSTOM_FEE_SCHEDULE_KEY -> ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
            case INVALID_TOKEN_MINT_METADATA -> ResponseCodeEnum.INVALID_TOKEN_MINT_METADATA;
            case INVALID_TOKEN_BURN_METADATA -> ResponseCodeEnum.INVALID_TOKEN_BURN_METADATA;
            case CURRENT_TREASURY_STILL_OWNS_NFTS -> ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
            case ACCOUNT_STILL_OWNS_NFTS -> ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
            case TREASURY_MUST_OWN_BURNED_NFT -> ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;
            case ACCOUNT_DOES_NOT_OWN_WIPED_NFT -> ResponseCodeEnum.ACCOUNT_DOES_NOT_OWN_WIPED_NFT;
            case ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON -> ResponseCodeEnum
                    .ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
            case MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED -> ResponseCodeEnum
                    .MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED;
            case PAYER_ACCOUNT_DELETED -> ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
            case CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH -> ResponseCodeEnum
                    .CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
            case CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS -> ResponseCodeEnum
                    .CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
            case INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE -> ResponseCodeEnum
                    .INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
            case SERIAL_NUMBER_LIMIT_REACHED -> ResponseCodeEnum.SERIAL_NUMBER_LIMIT_REACHED;
            case CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE -> ResponseCodeEnum
                    .CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
            case NO_REMAINING_AUTOMATIC_ASSOCIATIONS -> ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
            case EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT -> ResponseCodeEnum
                    .EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
            case REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT -> ResponseCodeEnum
                    .REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
            case TOKEN_IS_PAUSED -> ResponseCodeEnum.TOKEN_IS_PAUSED;
            case TOKEN_HAS_NO_PAUSE_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
            case INVALID_PAUSE_KEY -> ResponseCodeEnum.INVALID_PAUSE_KEY;
            case FREEZE_UPDATE_FILE_DOES_NOT_EXIST -> ResponseCodeEnum.FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
            case FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH -> ResponseCodeEnum.FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
            case NO_UPGRADE_HAS_BEEN_PREPARED -> ResponseCodeEnum.NO_UPGRADE_HAS_BEEN_PREPARED;
            case NO_FREEZE_IS_SCHEDULED -> ResponseCodeEnum.NO_FREEZE_IS_SCHEDULED;
            case UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE -> ResponseCodeEnum
                    .UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE;
            case FREEZE_START_TIME_MUST_BE_FUTURE -> ResponseCodeEnum.FREEZE_START_TIME_MUST_BE_FUTURE;
            case PREPARED_UPDATE_FILE_IS_IMMUTABLE -> ResponseCodeEnum.PREPARED_UPDATE_FILE_IS_IMMUTABLE;
            case FREEZE_ALREADY_SCHEDULED -> ResponseCodeEnum.FREEZE_ALREADY_SCHEDULED;
            case FREEZE_UPGRADE_IN_PROGRESS -> ResponseCodeEnum.FREEZE_UPGRADE_IN_PROGRESS;
            case UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED -> ResponseCodeEnum.UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED;
            case UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED -> ResponseCodeEnum.UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED;
            case CONSENSUS_GAS_EXHAUSTED -> ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
            case REVERTED_SUCCESS -> ResponseCodeEnum.REVERTED_SUCCESS;
            case MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED -> ResponseCodeEnum
                    .MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
            case INVALID_ALIAS_KEY -> ResponseCodeEnum.INVALID_ALIAS_KEY;
            case UNEXPECTED_TOKEN_DECIMALS -> ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;
            case INVALID_PROXY_ACCOUNT_ID -> ResponseCodeEnum.INVALID_PROXY_ACCOUNT_ID;
            case INVALID_TRANSFER_ACCOUNT_ID -> ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
            case INVALID_FEE_COLLECTOR_ACCOUNT_ID -> ResponseCodeEnum.INVALID_FEE_COLLECTOR_ACCOUNT_ID;
            case ALIAS_IS_IMMUTABLE -> ResponseCodeEnum.ALIAS_IS_IMMUTABLE;
            case SPENDER_ACCOUNT_SAME_AS_OWNER -> ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
            case AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY -> ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
            case NEGATIVE_ALLOWANCE_AMOUNT -> ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
            case CANNOT_APPROVE_FOR_ALL_FUNGIBLE_COMMON -> ResponseCodeEnum.CANNOT_APPROVE_FOR_ALL_FUNGIBLE_COMMON;
            case SPENDER_DOES_NOT_HAVE_ALLOWANCE -> ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
            case AMOUNT_EXCEEDS_ALLOWANCE -> ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
            case MAX_ALLOWANCES_EXCEEDED -> ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
            case EMPTY_ALLOWANCES -> ResponseCodeEnum.EMPTY_ALLOWANCES;
            case SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES -> ResponseCodeEnum.SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
            case REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES -> ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
            case FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES -> ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
            case NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES -> ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
            case INVALID_ALLOWANCE_OWNER_ID -> ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
            case INVALID_ALLOWANCE_SPENDER_ID -> ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
            case REPEATED_ALLOWANCES_TO_DELETE -> ResponseCodeEnum.REPEATED_ALLOWANCES_TO_DELETE;
            case INVALID_DELEGATING_SPENDER -> ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
            case DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL -> ResponseCodeEnum
                    .DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
            case DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL -> ResponseCodeEnum
                    .DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
            case SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE -> ResponseCodeEnum
                    .SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE;
            case SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME -> ResponseCodeEnum
                    .SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME;
            case SCHEDULE_FUTURE_THROTTLE_EXCEEDED -> ResponseCodeEnum.SCHEDULE_FUTURE_THROTTLE_EXCEEDED;
            case SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED -> ResponseCodeEnum.SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED;
            case INVALID_ETHEREUM_TRANSACTION -> ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
            case WRONG_CHAIN_ID -> ResponseCodeEnum.WRONG_CHAIN_ID;
            case WRONG_NONCE -> ResponseCodeEnum.WRONG_NONCE;
            case ACCESS_LIST_UNSUPPORTED -> ResponseCodeEnum.ACCESS_LIST_UNSUPPORTED;
            case SCHEDULE_PENDING_EXPIRATION -> ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
            case CONTRACT_IS_TOKEN_TREASURY -> ResponseCodeEnum.CONTRACT_IS_TOKEN_TREASURY;
            case CONTRACT_HAS_NON_ZERO_TOKEN_BALANCES -> ResponseCodeEnum.CONTRACT_HAS_NON_ZERO_TOKEN_BALANCES;
            case CONTRACT_EXPIRED_AND_PENDING_REMOVAL -> ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
            case CONTRACT_HAS_NO_AUTO_RENEW_ACCOUNT -> ResponseCodeEnum.CONTRACT_HAS_NO_AUTO_RENEW_ACCOUNT;
            case PERMANENT_REMOVAL_REQUIRES_SYSTEM_INITIATION -> ResponseCodeEnum
                    .PERMANENT_REMOVAL_REQUIRES_SYSTEM_INITIATION;
            case PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED -> ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
            case SELF_STAKING_IS_NOT_ALLOWED -> ResponseCodeEnum.SELF_STAKING_IS_NOT_ALLOWED;
            case INVALID_STAKING_ID -> ResponseCodeEnum.INVALID_STAKING_ID;
            case STAKING_NOT_ENABLED -> ResponseCodeEnum.STAKING_NOT_ENABLED;
            case INVALID_PRNG_RANGE -> ResponseCodeEnum.INVALID_PRNG_RANGE;
            case MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED -> ResponseCodeEnum
                    .MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
            case INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE -> ResponseCodeEnum
                    .INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
            case INSUFFICIENT_BALANCES_FOR_STORAGE_RENT -> ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_STORAGE_RENT;
            case MAX_CHILD_RECORDS_EXCEEDED -> ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
            case INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES -> ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES;
            case TRANSACTION_HAS_UNKNOWN_FIELDS -> ResponseCodeEnum.TRANSACTION_HAS_UNKNOWN_FIELDS;
            case ACCOUNT_IS_IMMUTABLE -> ResponseCodeEnum.ACCOUNT_IS_IMMUTABLE;
            case ALIAS_ALREADY_ASSIGNED -> ResponseCodeEnum.ALIAS_ALREADY_ASSIGNED;
            case INVALID_METADATA_KEY -> ResponseCodeEnum.INVALID_METADATA_KEY;
            case MISSING_TOKEN_METADATA -> ResponseCodeEnum.MISSING_TOKEN_METADATA;
            case TOKEN_HAS_NO_METADATA_KEY -> ResponseCodeEnum.TOKEN_HAS_NO_METADATA_KEY;
            case MISSING_SERIAL_NUMBERS -> ResponseCodeEnum.MISSING_SERIAL_NUMBERS;
            case UNRECOGNIZED -> throw new RuntimeException("UNRECOGNIZED Response code!");
        };
    }

    public static <T extends Record> Bytes asWrappedBytes(@NonNull Codec<T> codec, @NonNull T item) {
        return Bytes.wrap(asBytes(requireNonNull(codec), requireNonNull(item)));
    }

    public static <T extends Record> byte[] asBytes(@NonNull Codec<T> codec, @NonNull T tx) {
        requireNonNull(codec);
        requireNonNull(tx);
        try {
            final var bytes = new ByteArrayOutputStream();
            codec.write(tx, new WritableStreamingData(bytes));
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Unable to convert from PBJ to bytes", e);
        }
    }

    public static SemanticVersion toPbj(@NonNull com.hederahashgraph.api.proto.java.SemanticVersion v) {
        requireNonNull(v);
        return SemanticVersion.newBuilder()
                .major(v.getMajor())
                .minor(v.getMinor())
                .pre(v.getPre())
                .patch(v.getPatch())
                .build(v.getBuild())
                .build();
    }

    public static @NonNull Transaction toPbj(final @NonNull com.hederahashgraph.api.proto.java.Transaction t) {
        return protoToPbj(requireNonNull(t), Transaction.class);
    }

    public static Timestamp toPbj(@NonNull com.hederahashgraph.api.proto.java.Timestamp t) {
        requireNonNull(t);
        return Timestamp.newBuilder()
                .seconds(t.getSeconds())
                .nanos(t.getNanos())
                .build();
    }

    public static com.hederahashgraph.api.proto.java.Timestamp fromPbj(@NonNull Timestamp now) {
        requireNonNull(now);
        return com.hederahashgraph.api.proto.java.Timestamp.newBuilder()
                .setSeconds(now.seconds())
                .setNanos(now.nanos())
                .build();
    }

    public static com.hederahashgraph.api.proto.java.TopicID fromPbj(@NonNull TopicID id) {
        requireNonNull(id);
        return com.hederahashgraph.api.proto.java.TopicID.newBuilder()
                .setShardNum(id.shardNum())
                .setRealmNum(id.realmNum())
                .setTopicNum(id.topicNum())
                .build();
    }

    public static com.hederahashgraph.api.proto.java.TokenID fromPbj(@NonNull TokenID id) {
        requireNonNull(id);
        return com.hederahashgraph.api.proto.java.TokenID.newBuilder()
                .setShardNum(id.shardNum())
                .setRealmNum(id.realmNum())
                .setTokenNum(id.tokenNum())
                .build();
    }

    public static com.hederahashgraph.api.proto.java.ResponseCodeEnum fromPbj(@NonNull ResponseCodeEnum status) {
        return switch (requireNonNull(status)) {
            case OK -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
            case INVALID_TRANSACTION -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
            case PAYER_ACCOUNT_NOT_FOUND -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
            case INVALID_NODE_ACCOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
            case TRANSACTION_EXPIRED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
            case INVALID_TRANSACTION_START -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_TRANSACTION_START;
            case INVALID_TRANSACTION_DURATION -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_TRANSACTION_DURATION;
            case INVALID_SIGNATURE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
            case MEMO_TOO_LONG -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
            case INSUFFICIENT_TX_FEE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
            case INSUFFICIENT_PAYER_BALANCE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INSUFFICIENT_PAYER_BALANCE;
            case DUPLICATE_TRANSACTION -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
            case BUSY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
            case NOT_SUPPORTED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
            case INVALID_FILE_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
            case INVALID_ACCOUNT_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
            case INVALID_CONTRACT_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
            case INVALID_TRANSACTION_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
            case RECEIPT_NOT_FOUND -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECEIPT_NOT_FOUND;
            case RECORD_NOT_FOUND -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
            case INVALID_SOLIDITY_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ID;
            case UNKNOWN -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
            case SUCCESS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
            case FAIL_INVALID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
            case FAIL_FEE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_FEE;
            case FAIL_BALANCE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_BALANCE;
            case KEY_REQUIRED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
            case BAD_ENCODING -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
            case INSUFFICIENT_ACCOUNT_BALANCE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INSUFFICIENT_ACCOUNT_BALANCE;
            case INVALID_SOLIDITY_ADDRESS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_SOLIDITY_ADDRESS;
            case INSUFFICIENT_GAS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
            case CONTRACT_SIZE_LIMIT_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CONTRACT_SIZE_LIMIT_EXCEEDED;
            case LOCAL_CALL_MODIFICATION_EXCEPTION -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .LOCAL_CALL_MODIFICATION_EXCEPTION;
            case CONTRACT_REVERT_EXECUTED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CONTRACT_REVERT_EXECUTED;
            case CONTRACT_EXECUTION_EXCEPTION -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CONTRACT_EXECUTION_EXCEPTION;
            case INVALID_RECEIVING_NODE_ACCOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_RECEIVING_NODE_ACCOUNT;
            case MISSING_QUERY_HEADER -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_QUERY_HEADER;
            case ACCOUNT_UPDATE_FAILED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_UPDATE_FAILED;
            case INVALID_KEY_ENCODING -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KEY_ENCODING;
            case NULL_SOLIDITY_ADDRESS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.NULL_SOLIDITY_ADDRESS;
            case CONTRACT_UPDATE_FAILED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_UPDATE_FAILED;
            case INVALID_QUERY_HEADER -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_QUERY_HEADER;
            case INVALID_FEE_SUBMITTED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FEE_SUBMITTED;
            case INVALID_PAYER_SIGNATURE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
            case KEY_NOT_PROVIDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_NOT_PROVIDED;
            case INVALID_EXPIRATION_TIME -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
            case NO_WACL_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_WACL_KEY;
            case FILE_CONTENT_EMPTY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_CONTENT_EMPTY;
            case INVALID_ACCOUNT_AMOUNTS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
            case EMPTY_TRANSACTION_BODY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TRANSACTION_BODY;
            case INVALID_TRANSACTION_BODY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_TRANSACTION_BODY;
            case INVALID_SIGNATURE_TYPE_MISMATCHING_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_SIGNATURE_TYPE_MISMATCHING_KEY;
            case INVALID_SIGNATURE_COUNT_MISMATCHING_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_SIGNATURE_COUNT_MISMATCHING_KEY;
            case EMPTY_LIVE_HASH_BODY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_LIVE_HASH_BODY;
            case EMPTY_LIVE_HASH -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_LIVE_HASH;
            case EMPTY_LIVE_HASH_KEYS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_LIVE_HASH_KEYS;
            case INVALID_LIVE_HASH_SIZE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_LIVE_HASH_SIZE;
            case EMPTY_QUERY_BODY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_QUERY_BODY;
            case EMPTY_LIVE_HASH_QUERY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_LIVE_HASH_QUERY;
            case LIVE_HASH_NOT_FOUND -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.LIVE_HASH_NOT_FOUND;
            case ACCOUNT_ID_DOES_NOT_EXIST -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .ACCOUNT_ID_DOES_NOT_EXIST;
            case LIVE_HASH_ALREADY_EXISTS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .LIVE_HASH_ALREADY_EXISTS;
            case INVALID_FILE_WACL -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_WACL;
            case SERIALIZATION_FAILED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERIALIZATION_FAILED;
            case TRANSACTION_OVERSIZE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
            case TRANSACTION_TOO_MANY_LAYERS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TRANSACTION_TOO_MANY_LAYERS;
            case CONTRACT_DELETED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
            case PLATFORM_NOT_ACTIVE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
            case KEY_PREFIX_MISMATCH -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
            case PLATFORM_TRANSACTION_NOT_CREATED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .PLATFORM_TRANSACTION_NOT_CREATED;
            case INVALID_RENEWAL_PERIOD -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
            case INVALID_PAYER_ACCOUNT_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_PAYER_ACCOUNT_ID;
            case ACCOUNT_DELETED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
            case FILE_DELETED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
            case ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
            case SETTING_NEGATIVE_ACCOUNT_BALANCE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SETTING_NEGATIVE_ACCOUNT_BALANCE;
            case OBTAINER_REQUIRED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_REQUIRED;
            case OBTAINER_SAME_CONTRACT_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .OBTAINER_SAME_CONTRACT_ID;
            case OBTAINER_DOES_NOT_EXIST -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_DOES_NOT_EXIST;
            case MODIFYING_IMMUTABLE_CONTRACT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .MODIFYING_IMMUTABLE_CONTRACT;
            case FILE_SYSTEM_EXCEPTION -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_SYSTEM_EXCEPTION;
            case AUTORENEW_DURATION_NOT_IN_RANGE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .AUTORENEW_DURATION_NOT_IN_RANGE;
            case ERROR_DECODING_BYTESTRING -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .ERROR_DECODING_BYTESTRING;
            case CONTRACT_FILE_EMPTY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_FILE_EMPTY;
            case CONTRACT_BYTECODE_EMPTY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_BYTECODE_EMPTY;
            case INVALID_INITIAL_BALANCE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
            case INVALID_RECEIVE_RECORD_THRESHOLD -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_RECEIVE_RECORD_THRESHOLD;
            case INVALID_SEND_RECORD_THRESHOLD -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_SEND_RECORD_THRESHOLD;
            case ACCOUNT_IS_NOT_GENESIS_ACCOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .ACCOUNT_IS_NOT_GENESIS_ACCOUNT;
            case PAYER_ACCOUNT_UNAUTHORIZED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .PAYER_ACCOUNT_UNAUTHORIZED;
            case INVALID_FREEZE_TRANSACTION_BODY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_FREEZE_TRANSACTION_BODY;
            case FREEZE_TRANSACTION_BODY_NOT_FOUND -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .FREEZE_TRANSACTION_BODY_NOT_FOUND;
            case TRANSFER_LIST_SIZE_LIMIT_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
            case RESULT_SIZE_LIMIT_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .RESULT_SIZE_LIMIT_EXCEEDED;
            case NOT_SPECIAL_ACCOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SPECIAL_ACCOUNT;
            case CONTRACT_NEGATIVE_GAS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
            case CONTRACT_NEGATIVE_VALUE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
            case INVALID_FEE_FILE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FEE_FILE;
            case INVALID_EXCHANGE_RATE_FILE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_EXCHANGE_RATE_FILE;
            case INSUFFICIENT_LOCAL_CALL_GAS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INSUFFICIENT_LOCAL_CALL_GAS;
            case ENTITY_NOT_ALLOWED_TO_DELETE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .ENTITY_NOT_ALLOWED_TO_DELETE;
            case AUTHORIZATION_FAILED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
            case FILE_UPLOADED_PROTO_INVALID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .FILE_UPLOADED_PROTO_INVALID;
            case FILE_UPLOADED_PROTO_NOT_SAVED_TO_DISK -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .FILE_UPLOADED_PROTO_NOT_SAVED_TO_DISK;
            case FEE_SCHEDULE_FILE_PART_UPLOADED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .FEE_SCHEDULE_FILE_PART_UPLOADED;
            case EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED;
            case MAX_CONTRACT_STORAGE_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .MAX_CONTRACT_STORAGE_EXCEEDED;
            case TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;
            case TOTAL_LEDGER_BALANCE_INVALID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TOTAL_LEDGER_BALANCE_INVALID;
            case EXPIRATION_REDUCTION_NOT_ALLOWED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .EXPIRATION_REDUCTION_NOT_ALLOWED;
            case MAX_GAS_LIMIT_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
            case MAX_FILE_SIZE_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
            case RECEIVER_SIG_REQUIRED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECEIVER_SIG_REQUIRED;
            case INVALID_TOPIC_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
            case INVALID_ADMIN_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
            case INVALID_SUBMIT_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUBMIT_KEY;
            case UNAUTHORIZED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
            case INVALID_TOPIC_MESSAGE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_MESSAGE;
            case INVALID_AUTORENEW_ACCOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_AUTORENEW_ACCOUNT;
            case AUTORENEW_ACCOUNT_NOT_ALLOWED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .AUTORENEW_ACCOUNT_NOT_ALLOWED;
            case TOPIC_EXPIRED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;
            case INVALID_CHUNK_NUMBER -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_NUMBER;
            case INVALID_CHUNK_TRANSACTION_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_CHUNK_TRANSACTION_ID;
            case ACCOUNT_FROZEN_FOR_TOKEN -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .ACCOUNT_FROZEN_FOR_TOKEN;
            case TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
            case INVALID_TOKEN_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
            case INVALID_TOKEN_DECIMALS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
            case INVALID_TOKEN_INITIAL_SUPPLY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_TOKEN_INITIAL_SUPPLY;
            case INVALID_TREASURY_ACCOUNT_FOR_TOKEN -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
            case INVALID_TOKEN_SYMBOL -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
            case TOKEN_HAS_NO_FREEZE_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
            case TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
            case MISSING_TOKEN_SYMBOL -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
            case TOKEN_SYMBOL_TOO_LONG -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
            case ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
            case TOKEN_HAS_NO_KYC_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
            case INSUFFICIENT_TOKEN_BALANCE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INSUFFICIENT_TOKEN_BALANCE;
            case TOKEN_WAS_DELETED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
            case TOKEN_HAS_NO_SUPPLY_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
            case TOKEN_HAS_NO_WIPE_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
            case INVALID_TOKEN_MINT_AMOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_TOKEN_MINT_AMOUNT;
            case INVALID_TOKEN_BURN_AMOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_TOKEN_BURN_AMOUNT;
            case TOKEN_NOT_ASSOCIATED_TO_ACCOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
            case CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
            case INVALID_KYC_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
            case INVALID_WIPE_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
            case INVALID_FREEZE_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
            case INVALID_SUPPLY_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
            case MISSING_TOKEN_NAME -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_NAME;
            case TOKEN_NAME_TOO_LONG -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
            case INVALID_WIPING_AMOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
            case TOKEN_IS_IMMUTABLE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
            case TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
            case TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
            case ACCOUNT_IS_TREASURY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
            case TOKEN_ID_REPEATED_IN_TOKEN_LIST -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TOKEN_ID_REPEATED_IN_TOKEN_LIST;
            case TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
            case EMPTY_TOKEN_TRANSFER_BODY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .EMPTY_TOKEN_TRANSFER_BODY;
            case EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
            case INVALID_SCHEDULE_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
            case SCHEDULE_IS_IMMUTABLE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
            case INVALID_SCHEDULE_PAYER_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_SCHEDULE_PAYER_ID;
            case INVALID_SCHEDULE_ACCOUNT_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_SCHEDULE_ACCOUNT_ID;
            case NO_NEW_VALID_SIGNATURES -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
            case UNRESOLVABLE_REQUIRED_SIGNERS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .UNRESOLVABLE_REQUIRED_SIGNERS;
            case SCHEDULED_TRANSACTION_NOT_IN_WHITELIST -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
            case SOME_SIGNATURES_WERE_INVALID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SOME_SIGNATURES_WERE_INVALID;
            case TRANSACTION_ID_FIELD_NOT_ALLOWED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TRANSACTION_ID_FIELD_NOT_ALLOWED;
            case IDENTICAL_SCHEDULE_ALREADY_CREATED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .IDENTICAL_SCHEDULE_ALREADY_CREATED;
            case INVALID_ZERO_BYTE_IN_STRING -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_ZERO_BYTE_IN_STRING;
            case SCHEDULE_ALREADY_DELETED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SCHEDULE_ALREADY_DELETED;
            case SCHEDULE_ALREADY_EXECUTED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SCHEDULE_ALREADY_EXECUTED;
            case MESSAGE_SIZE_TOO_LARGE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
            case OPERATION_REPEATED_IN_BUCKET_GROUPS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .OPERATION_REPEATED_IN_BUCKET_GROUPS;
            case BUCKET_CAPACITY_OVERFLOW -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .BUCKET_CAPACITY_OVERFLOW;
            case NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION;
            case BUCKET_HAS_NO_THROTTLE_GROUPS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .BUCKET_HAS_NO_THROTTLE_GROUPS;
            case THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC;
            case SUCCESS_BUT_MISSING_EXPECTED_OPERATION -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
            case UNPARSEABLE_THROTTLE_DEFINITIONS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .UNPARSEABLE_THROTTLE_DEFINITIONS;
            case INVALID_THROTTLE_DEFINITIONS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_THROTTLE_DEFINITIONS;
            case ACCOUNT_EXPIRED_AND_PENDING_REMOVAL -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
            case INVALID_TOKEN_MAX_SUPPLY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_TOKEN_MAX_SUPPLY;
            case INVALID_TOKEN_NFT_SERIAL_NUMBER -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_TOKEN_NFT_SERIAL_NUMBER;
            case INVALID_NFT_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
            case METADATA_TOO_LONG -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.METADATA_TOO_LONG;
            case BATCH_SIZE_LIMIT_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .BATCH_SIZE_LIMIT_EXCEEDED;
            case INVALID_QUERY_RANGE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_QUERY_RANGE;
            case FRACTION_DIVIDES_BY_ZERO -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .FRACTION_DIVIDES_BY_ZERO;
            case INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE;
            case CUSTOM_FEES_LIST_TOO_LONG -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CUSTOM_FEES_LIST_TOO_LONG;
            case INVALID_CUSTOM_FEE_COLLECTOR -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_CUSTOM_FEE_COLLECTOR;
            case INVALID_TOKEN_ID_IN_CUSTOM_FEES -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_TOKEN_ID_IN_CUSTOM_FEES;
            case TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
            case TOKEN_MAX_SUPPLY_REACHED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TOKEN_MAX_SUPPLY_REACHED;
            case SENDER_DOES_NOT_OWN_NFT_SERIAL_NO -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
            case CUSTOM_FEE_NOT_FULLY_SPECIFIED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CUSTOM_FEE_NOT_FULLY_SPECIFIED;
            case CUSTOM_FEE_MUST_BE_POSITIVE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CUSTOM_FEE_MUST_BE_POSITIVE;
            case TOKEN_HAS_NO_FEE_SCHEDULE_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
            case CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
            case ROYALTY_FRACTION_CANNOT_EXCEED_ONE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .ROYALTY_FRACTION_CANNOT_EXCEED_ONE;
            case FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT;
            case CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES;
            case CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON;
            case CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON -> com.hederahashgraph.api.proto.java
                    .ResponseCodeEnum.CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
            case INVALID_CUSTOM_FEE_SCHEDULE_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_CUSTOM_FEE_SCHEDULE_KEY;
            case INVALID_TOKEN_MINT_METADATA -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_TOKEN_MINT_METADATA;
            case INVALID_TOKEN_BURN_METADATA -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_TOKEN_BURN_METADATA;
            case CURRENT_TREASURY_STILL_OWNS_NFTS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CURRENT_TREASURY_STILL_OWNS_NFTS;
            case ACCOUNT_STILL_OWNS_NFTS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_STILL_OWNS_NFTS;
            case TREASURY_MUST_OWN_BURNED_NFT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TREASURY_MUST_OWN_BURNED_NFT;
            case ACCOUNT_DOES_NOT_OWN_WIPED_NFT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .ACCOUNT_DOES_NOT_OWN_WIPED_NFT;
            case ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON -> com.hederahashgraph.api.proto.java
                    .ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
            case MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED;
            case PAYER_ACCOUNT_DELETED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
            case CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
            case CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
            case INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE -> com.hederahashgraph.api.proto.java
                    .ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
            case SERIAL_NUMBER_LIMIT_REACHED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SERIAL_NUMBER_LIMIT_REACHED;
            case CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE -> com.hederahashgraph.api.proto.java
                    .ResponseCodeEnum.CUSTOM_ROYALTY_FEE_ONLY_ALLOWED_FOR_NON_FUNGIBLE_UNIQUE;
            case NO_REMAINING_AUTOMATIC_ASSOCIATIONS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
            case EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT -> com.hederahashgraph.api.proto.java
                    .ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
            case REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT -> com.hederahashgraph.api.proto.java
                    .ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
            case TOKEN_IS_PAUSED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
            case TOKEN_HAS_NO_PAUSE_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
            case INVALID_PAUSE_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAUSE_KEY;
            case FREEZE_UPDATE_FILE_DOES_NOT_EXIST -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
            case FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
            case NO_UPGRADE_HAS_BEEN_PREPARED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .NO_UPGRADE_HAS_BEEN_PREPARED;
            case NO_FREEZE_IS_SCHEDULED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_FREEZE_IS_SCHEDULED;
            case UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE;
            case FREEZE_START_TIME_MUST_BE_FUTURE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .FREEZE_START_TIME_MUST_BE_FUTURE;
            case PREPARED_UPDATE_FILE_IS_IMMUTABLE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .PREPARED_UPDATE_FILE_IS_IMMUTABLE;
            case FREEZE_ALREADY_SCHEDULED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .FREEZE_ALREADY_SCHEDULED;
            case FREEZE_UPGRADE_IN_PROGRESS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .FREEZE_UPGRADE_IN_PROGRESS;
            case UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED;
            case UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED;
            case CONSENSUS_GAS_EXHAUSTED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
            case REVERTED_SUCCESS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
            case MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
            case INVALID_ALIAS_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
            case UNEXPECTED_TOKEN_DECIMALS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .UNEXPECTED_TOKEN_DECIMALS;
            case INVALID_PROXY_ACCOUNT_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_PROXY_ACCOUNT_ID;
            case INVALID_TRANSFER_ACCOUNT_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_TRANSFER_ACCOUNT_ID;
            case INVALID_FEE_COLLECTOR_ACCOUNT_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_FEE_COLLECTOR_ACCOUNT_ID;
            case ALIAS_IS_IMMUTABLE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.ALIAS_IS_IMMUTABLE;
            case SPENDER_ACCOUNT_SAME_AS_OWNER -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SPENDER_ACCOUNT_SAME_AS_OWNER;
            case AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
            case NEGATIVE_ALLOWANCE_AMOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .NEGATIVE_ALLOWANCE_AMOUNT;
            case CANNOT_APPROVE_FOR_ALL_FUNGIBLE_COMMON -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CANNOT_APPROVE_FOR_ALL_FUNGIBLE_COMMON;
            case SPENDER_DOES_NOT_HAVE_ALLOWANCE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SPENDER_DOES_NOT_HAVE_ALLOWANCE;
            case AMOUNT_EXCEEDS_ALLOWANCE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .AMOUNT_EXCEEDS_ALLOWANCE;
            case MAX_ALLOWANCES_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
            case EMPTY_ALLOWANCES -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
            case SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
            case REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
            case FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
            case NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
            case INVALID_ALLOWANCE_OWNER_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_ALLOWANCE_OWNER_ID;
            case INVALID_ALLOWANCE_SPENDER_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_ALLOWANCE_SPENDER_ID;
            case REPEATED_ALLOWANCES_TO_DELETE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .REPEATED_ALLOWANCES_TO_DELETE;
            case INVALID_DELEGATING_SPENDER -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_DELEGATING_SPENDER;
            case DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
            case DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
            case SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE;
            case SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME -> com.hederahashgraph.api.proto.java
                    .ResponseCodeEnum.SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME;
            case SCHEDULE_FUTURE_THROTTLE_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SCHEDULE_FUTURE_THROTTLE_EXCEEDED;
            case SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED;
            case INVALID_ETHEREUM_TRANSACTION -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_ETHEREUM_TRANSACTION;
            case WRONG_CHAIN_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.WRONG_CHAIN_ID;
            case WRONG_NONCE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.WRONG_NONCE;
            case ACCESS_LIST_UNSUPPORTED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCESS_LIST_UNSUPPORTED;
            case SCHEDULE_PENDING_EXPIRATION -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SCHEDULE_PENDING_EXPIRATION;
            case CONTRACT_IS_TOKEN_TREASURY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CONTRACT_IS_TOKEN_TREASURY;
            case CONTRACT_HAS_NON_ZERO_TOKEN_BALANCES -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CONTRACT_HAS_NON_ZERO_TOKEN_BALANCES;
            case CONTRACT_EXPIRED_AND_PENDING_REMOVAL -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
            case CONTRACT_HAS_NO_AUTO_RENEW_ACCOUNT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .CONTRACT_HAS_NO_AUTO_RENEW_ACCOUNT;
            case PERMANENT_REMOVAL_REQUIRES_SYSTEM_INITIATION -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .PERMANENT_REMOVAL_REQUIRES_SYSTEM_INITIATION;
            case PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
            case SELF_STAKING_IS_NOT_ALLOWED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .SELF_STAKING_IS_NOT_ALLOWED;
            case INVALID_STAKING_ID -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
            case STAKING_NOT_ENABLED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.STAKING_NOT_ENABLED;
            case INVALID_PRNG_RANGE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PRNG_RANGE;
            case MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
            case INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
            case INSUFFICIENT_BALANCES_FOR_STORAGE_RENT -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INSUFFICIENT_BALANCES_FOR_STORAGE_RENT;
            case MAX_CHILD_RECORDS_EXCEEDED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .MAX_CHILD_RECORDS_EXCEEDED;
            case INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES;
            case TRANSACTION_HAS_UNKNOWN_FIELDS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TRANSACTION_HAS_UNKNOWN_FIELDS;
            case ACCOUNT_IS_IMMUTABLE -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_IMMUTABLE;
            case ALIAS_ALREADY_ASSIGNED -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.ALIAS_ALREADY_ASSIGNED;
            case INVALID_METADATA_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_METADATA_KEY;
            case TOKEN_HAS_NO_METADATA_KEY -> com.hederahashgraph.api.proto.java.ResponseCodeEnum
                    .TOKEN_HAS_NO_METADATA_KEY;
            case MISSING_TOKEN_METADATA -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_METADATA;
            case MISSING_SERIAL_NUMBERS -> com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_SERIAL_NUMBERS;
                //            case UNRECOGNIZED -> throw new RuntimeException("UNRECOGNIZED Response code!");
        };
    }

    public static com.hederahashgraph.api.proto.java.Query toProtoQuery(final Query query) {
        return pbjToProto(query, Query.class, com.hederahashgraph.api.proto.java.Query.class);
    }

    public static TopicID toPbjTopicId(final com.hederahashgraph.api.proto.java.TopicID topicId) {
        return protoToPbj(topicId, TopicID.class);
    }

    public static <T extends Record, R extends GeneratedMessageV3> R pbjToProto(
            final T pbj, final Class<T> pbjClass, final Class<R> protoClass) {
        try {
            final var codecField = pbjClass.getDeclaredField("PROTOBUF");
            final var codec = (Codec<T>) codecField.get(null);
            final var bytes = asBytes(codec, pbj);
            final var protocParser = protoClass.getMethod("parseFrom", byte[].class);
            return (R) protocParser.invoke(null, bytes);
        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            // Should be impossible, so just propagate an exception
            throw new RuntimeException("Invalid conversion to proto for " + pbjClass.getSimpleName(), e);
        }
    }

    private interface ProtoParser<R extends GeneratedMessageV3> {
        R parseFrom(byte[] bytes) throws InvalidProtocolBufferException;
    }

    private static <T extends Record, R extends GeneratedMessageV3> R explicitPbjToProto(
            @NonNull final T pbj, @NonNull final Codec<T> pbjCodec, @NonNull final ProtoParser<R> protoParser) {
        requireNonNull(pbj);
        requireNonNull(pbjCodec);
        requireNonNull(protoParser);
        try {
            return protoParser.parseFrom(asBytes(pbjCodec, pbj));
        } catch (InvalidProtocolBufferException e) {
            // Should be impossible
            throw new IllegalStateException("Serialization failure for " + pbj, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends GeneratedMessageV3, R extends Record> @NonNull R protoToPbj(
            @NonNull final T proto, @NonNull final Class<R> pbjClass) {
        try {
            final var bytes = requireNonNull(proto).toByteArray();
            final var codecField = requireNonNull(pbjClass).getDeclaredField("PROTOBUF");
            final var codec = (Codec<R>) codecField.get(null);
            return codec.parse(BufferedData.wrap(bytes));
        } catch (NoSuchFieldException | IllegalAccessException | ParseException e) {
            // Should be impossible, so just propagate an exception
            throw new RuntimeException("Invalid conversion to PBJ for " + pbjClass.getSimpleName(), e);
        }
    }

    /**
     * Converts a gRPC {@link com.hederahashgraph.api.proto.java.Key} to a PBJ {@link Key}.
     * (We will encounter gRPC keys until the handle workflow is using PBJ objects.)
     *
     * @param grpcKey the gRPC {@link com.hederahashgraph.api.proto.java.Key} to convert
     * @return the PBJ {@link Key}
     * @throws IllegalStateException if the conversion fails
     */
    public static @NonNull Key fromGrpcKey(@NonNull final com.hederahashgraph.api.proto.java.Key grpcKey) {
        try (final var bais = new ByteArrayInputStream(requireNonNull(grpcKey).toByteArray())) {
            final var stream = new ReadableStreamingData(bais);
            stream.limit(bais.available());
            return Key.PROTOBUF.parse(stream);
        } catch (final IOException | ParseException e) {
            // Should be impossible, so just propagate an exception
            throw new IllegalStateException("Invalid conversion to PBJ for Key", e);
        }
    }

    /**
     * Tries to convert a PBJ {@link Key} to a {@link JKey} (the only {@link HederaKey} implementation);
     * returns an empty optional if the conversion succeeds, but the resulting {@link JKey} is not valid.
     *
     * @param pbjKey the PBJ {@link Key} to convert
     * @return the converted {@link JKey} if valid, or an empty optional if invalid
     * @throws IllegalStateException if the conversion fails
     */
    public static @NonNull Optional<HederaKey> fromPbjKey(@Nullable final Key pbjKey) {
        if (pbjKey == null) {
            return Optional.empty();
        }
        try (final var baos = new ByteArrayOutputStream();
                final var dos = new WritableStreamingData(baos)) {
            Key.PROTOBUF.write(pbjKey, dos);
            final var grpcKey = com.hederahashgraph.api.proto.java.Key.parseFrom(baos.toByteArray());
            return asHederaKey(grpcKey);
        } catch (final IOException e) {
            // Should be impossible, so just propagate an exception
            throw new IllegalStateException("Invalid conversion from PBJ for Key", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Tries to convert a PBJ {@link Key} to a {@link JKey} unchecked (the only {@link HederaKey} implementation);
     * returns an empty optional if the conversion succeeds, but the resulting {@link JKey} is not valid.
     * this is just for testing because some of the key generated by seeded source are not valid
     * @param pbjKey the PBJ {@link Key} to convert
     * @return the converted {@link JKey} if valid, or an empty optional if invalid
     * @throws IllegalStateException if the conversion fails
     */
    public static @NonNull Optional<HederaKey> fromPbjKeyUnchecked(@Nullable final Key pbjKey) {
        if (pbjKey == null) {
            return Optional.empty();
        }
        try (final var baos = new ByteArrayOutputStream();
                final var dos = new WritableStreamingData(baos)) {
            Key.PROTOBUF.write(pbjKey, dos);
            final var grpcKey = com.hederahashgraph.api.proto.java.Key.parseFrom(baos.toByteArray());
            return asHederaKeyUnchecked(grpcKey);
        } catch (final IOException e) {
            // Should be impossible, so just propagate an exception
            throw new IllegalStateException("Invalid conversion from PBJ for Key", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Tries to convert a PBJ {@link ResponseType} to a {@link com.hederahashgraph.api.proto.java.ResponseType}
     * @param responseType the PBJ {@link ResponseType} to convert
     * @return the converted {@link com.hederahashgraph.api.proto.java.ResponseType} if valid
     */
    public static @NonNull com.hederahashgraph.api.proto.java.ResponseType fromPbjResponseType(
            @NonNull final ResponseType responseType) {
        return switch (requireNonNull(responseType)) {
            case ANSWER_ONLY -> com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
            case ANSWER_STATE_PROOF -> com.hederahashgraph.api.proto.java.ResponseType.ANSWER_STATE_PROOF;
            case COST_ANSWER -> com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
            case COST_ANSWER_STATE_PROOF -> com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER_STATE_PROOF;
        };
    }

    public static @NonNull Optional<SchedulableTransactionBody> toPbj(
            @Nullable final com.hederahashgraph.api.proto.java.SchedulableTransactionBody schedulableTransactionBody) {
        if (schedulableTransactionBody == null) {
            return Optional.empty();
        }
        try (final var baos = new ByteArrayOutputStream()) {
            schedulableTransactionBody.writeTo(baos);
            return Optional.of(SchedulableTransactionBody.PROTOBUF.parse(
                    Bytes.wrap(baos.toByteArray()).toReadableSequentialData()));
        } catch (final IOException | ParseException e) {
            // Should be impossible, so just propagate an exception
            throw new IllegalStateException("Invalid conversion from PBJ for SchedulableTransactionBody", e);
        }
    }

    public static @NonNull com.hederahashgraph.api.proto.java.SchedulableTransactionBody fromPbj(
            @NonNull SchedulableTransactionBody tx) {
        requireNonNull(tx);
        try {
            final var bytes = asBytes(SchedulableTransactionBody.PROTOBUF, tx);
            return com.hederahashgraph.api.proto.java.SchedulableTransactionBody.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public static Key asPbjKey(@NonNull final JKey jKey) {
        requireNonNull(jKey);
        try {
            return toPbj(JKey.mapJKey(jKey));
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NonNull CustomFee fromFcCustomFee(@Nullable final FcCustomFee fcFee) {
        try (final var bais =
                new ByteArrayInputStream(requireNonNull(fcFee).asGrpc().toByteArray())) {
            final var stream = new ReadableStreamingData(bais);
            stream.limit(bais.available());
            return CustomFee.PROTOBUF.parse(stream);
        } catch (final IOException | ParseException e) {
            // Should be impossible, so just propagate an exception
            throw new IllegalStateException("Invalid conversion to PBJ for CustomFee", e);
        }
    }

    // Note: this method will throw an exception if <code>b</code>'s length is not representable as an int
    public static @NonNull byte[] asBytes(@NonNull Bytes b) {
        final var buf = new byte[Math.toIntExact(b.length())];
        b.getBytes(0, buf);
        return buf;
    }

    // Note: this method will throw an exception if <code>b</code>'s length is not representable as an int
    public static @NonNull byte[] asBytes(@NonNull BufferedData b) {
        final var buf = new byte[Math.toIntExact(b.position())];
        b.readBytes(buf);
        return buf;
    }

    public static @NonNull com.hederahashgraph.api.proto.java.ContractID fromPbj(final @NonNull ContractID contractID) {
        requireNonNull(contractID);
        return com.hederahashgraph.api.proto.java.ContractID.newBuilder()
                .setRealmNum(contractID.realmNum())
                .setShardNum(contractID.shardNum())
                .setContractNum(contractID.contractNumOrElse(0L))
                .setEvmAddress(ByteString.copyFrom(asBytes(contractID.evmAddressOrElse(Bytes.EMPTY))))
                .build();
    }

    public static com.hederahashgraph.api.proto.java.FileID fromPbj(final FileID someFileId) {
        return com.hederahashgraph.api.proto.java.FileID.newBuilder()
                .setRealmNum(someFileId.realmNum())
                .setShardNum(someFileId.shardNum())
                .setFileNum(someFileId.fileNum())
                .build();
    }

    @NonNull
    public static com.hederahashgraph.api.proto.java.CustomFee fromPbj(@NonNull final CustomFee customFee) {
        return explicitPbjToProto(
                customFee, CustomFee.PROTOBUF, com.hederahashgraph.api.proto.java.CustomFee::parseFrom);
    }

    @NonNull
    public static com.hederahashgraph.api.proto.java.Fraction fromPbj(@NonNull final Fraction fraction) {
        var builder = com.hederahashgraph.api.proto.java.Fraction.newBuilder();
        builder.setNumerator(fraction.numerator());
        builder.setDenominator(fraction.denominator());
        return builder.build();
    }

    public static FileID toPbj(com.hederahashgraph.api.proto.java.FileID fileID) {
        return protoToPbj(fileID, FileID.class);
    }

    @NonNull
    public static com.hederahashgraph.api.proto.java.File fromPbj(@Nullable File file) {
        var builder = com.hederahashgraph.api.proto.java.File.newBuilder();
        if (file != null) {
            builder.setFileId(fromPbj(file.fileIdOrThrow()));
            builder.setExpirationSecond(file.expirationSecond());
            builder.setKeys(pbjToProto(
                    file.keysOrElse(KeyList.DEFAULT), KeyList.class, com.hederahashgraph.api.proto.java.KeyList.class));
            builder.setContents(ByteString.copyFrom(file.contents().toByteArray()));
            builder.setMemo(file.memo());
            builder.setDeleted(file.deleted());
        }
        return builder.build();
    }

    public static TopicID toPbj(com.hederahashgraph.api.proto.java.TopicID topicId) {
        return protoToPbj(topicId, TopicID.class);
    }

    public static SignatureMap toPbj(com.hederahashgraph.api.proto.java.SignatureMap sigMap) {
        return protoToPbj(sigMap, SignatureMap.class);
    }

    public static com.hederahashgraph.api.proto.java.SignatureMap fromPbj(SignatureMap signatureMap) {
        return pbjToProto(signatureMap, SignatureMap.class, com.hederahashgraph.api.proto.java.SignatureMap.class);
    }

    public static com.hederahashgraph.api.proto.java.QueryHeader fromPbj(QueryHeader queryHeader) {
        return pbjToProto(queryHeader, QueryHeader.class, com.hederahashgraph.api.proto.java.QueryHeader.class);
    }

    public static NetworkGetExecutionTimeQuery toPbj(
            com.hederahashgraph.api.proto.java.NetworkGetExecutionTimeQuery query) {
        return protoToPbj(query, NetworkGetExecutionTimeQuery.class);
    }

    public static DeterministicThrottle.UsageSnapshot fromPbj(ThrottleUsageSnapshot snapshot) {
        final var lastDecisionTime = snapshot.lastDecisionTime();
        if (lastDecisionTime == null) {
            return new DeterministicThrottle.UsageSnapshot(snapshot.used(), null);
        } else {
            return new DeterministicThrottle.UsageSnapshot(snapshot.used(), HapiUtils.asInstant(lastDecisionTime));
        }
    }

    public static ThrottleUsageSnapshot toPbj(DeterministicThrottle.UsageSnapshot snapshot) {
        final var lastDecisionTime = snapshot.lastDecisionTime();
        if (lastDecisionTime == null) {
            return new ThrottleUsageSnapshot(snapshot.used(), null);
        } else {
            return new ThrottleUsageSnapshot(snapshot.used(), HapiUtils.asTimestamp(lastDecisionTime));
        }
    }
}
