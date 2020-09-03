package com.hederahashgraph.builder;

/*-
 * ‌
 * Hedera Services API
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.base.Strings;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.LiveHash;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractGetBytecodeQuery;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.ContractGetRecordsQuery;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.CryptoGetLiveHashQuery;
import com.hederahashgraph.api.proto.java.CryptoGetInfoQuery;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileGetContentsQuery;
import com.hederahashgraph.api.proto.java.FileGetInfoQuery;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.GetBySolidityIDQuery;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseHeader.Builder;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetFastRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptQuery;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import java.util.List;

/**
 * @author Akshay
 * @Date : 7/2/2018
 */
public class RequestBuilder {

//  public static Transaction getCreateAccountBuilder(Long payerAccountNum, Long payerRealmNum,
//      Long payerShardNum,
//      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum, long transactionFee,
//      Timestamp startTime,
//      Duration transactionDuration, boolean generateRecord, String memo, int thresholdValue,
//      List<Key> keyList,
//      long initBal, long sendRecordThreshold, long receiveRecordThreshold, boolean receiverSign,
//      Duration autoRenew, SignatureList signatures) {
//    Key keys = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(keyList).build())
//        .build();
//    return getCreateAccountBuilder(payerAccountNum, payerRealmNum, payerShardNum,
//        nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee, startTime,
//        transactionDuration, generateRecord, memo, keys,
//        initBal, sendRecordThreshold, receiveRecordThreshold, receiverSign,
//        autoRenew, signatures);
//  }


  public static TransactionBody getCreateAccountTxBody(Long payerAccountNum, Long payerRealmNum,
      Long payerShardNum,
      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum, long transactionFee,
      Timestamp startTime,
      Duration transactionDuration, boolean generateRecord, String memo, int thresholdValue,
      List<Key> keyList,
      long initBal, long sendRecordThreshold, long receiveRecordThreshold, boolean receiverSign,
      Duration autoRenew, long proxyAccountNum, long proxyRealmNum, long proxyShardNum,
      long shardID, long realmID) {
    Key keys = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(keyList).build())
        .build();
    CryptoCreateTransactionBody createAccount = CryptoCreateTransactionBody.newBuilder()
        .setKey(keys)
        .setInitialBalance(initBal)
        .setProxyAccountID(getAccountIdBuild(proxyAccountNum, proxyRealmNum, proxyShardNum))
        .setReceiveRecordThreshold(receiveRecordThreshold)
        .setSendRecordThreshold(sendRecordThreshold).setReceiverSigRequired(receiverSign)
        .setRealmID(RealmID.newBuilder().setRealmNum(realmID).build())
        .setShardID(ShardID.newBuilder().setShardNum(shardID))
        .setAutoRenewPeriod(autoRenew).build();

    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
        nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee, startTime, transactionDuration,
        generateRecord, memo);
    body.setCryptoCreateAccount(createAccount);
    return body.build();
  }

//  public static Transaction getAccountUpdateRequest(AccountID accountID, Long payerAccountNum,
//      Long payerRealmNum,
//      Long payerShardNum, Long nodeAccountNum, Long nodeRealmNum,
//      Long nodeShardNum, long transactionFee, Timestamp startTime,
//      Duration transactionDuration, boolean generateRecord, String memo,
//      long sendRecordThreshold, long receiveRecordThreshold,
//      Duration autoRenew, SignatureList signatures) {
//
//    CryptoUpdateTransactionBody cryptoUpdate = CryptoUpdateTransactionBody.newBuilder()
//        .setAccountIDToUpdate(accountID)
//        .setAutoRenewPeriod(autoRenew).build();
//    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
//        nodeAccountNum,
//        nodeRealmNum, nodeShardNum, transactionFee, startTime,
//        transactionDuration, generateRecord, memo);
//    body.setCryptoUpdateAccount(cryptoUpdate);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }

  /**
   * Generates a transaction with a CryptoUpdateTransactionBody object pre-built by caller.
   *
   * @return transaction built
   */
//  public static Transaction getAccountUpdateRequest(AccountID accountID, Long payerAccountNum,
//      Long payerRealmNum,
//      Long payerShardNum, Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
//      long transactionFee,
//      Timestamp startTime, Duration transactionDuration, boolean generateRecord, String memo,
//      CryptoUpdateTransactionBody cryptoUpdate, SignatureList signatures) {
//
//    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
//        nodeAccountNum,
//        nodeRealmNum, nodeShardNum, transactionFee, startTime, transactionDuration, generateRecord,
//        memo);
//    body.setCryptoUpdateAccount(cryptoUpdate);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }

//  public static Transaction getAccountDeleteRequest(AccountID accountID, AccountID trasferAccountID,
//      Long payerAccountNum, Long payerRealmNum,
//      Long payerShardNum, Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
//      long transactionFee,
//      Timestamp startTime, Duration transactionDuration, boolean generateRecord, String memo,
//      SignatureList signatures) {
//    CryptoDeleteTransactionBody cryptoDelete = CryptoDeleteTransactionBody.newBuilder()
//        .setDeleteAccountID(accountID).setTransferAccountID(trasferAccountID).build();
//    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
//        nodeAccountNum,
//        nodeRealmNum, nodeShardNum, transactionFee, startTime, transactionDuration, generateRecord,
//        memo);
//    body.setCryptoDelete(cryptoDelete);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }

  private static TransactionBody.Builder getTransactionBody(Long payerAccountNum,
      Long payerRealmNum, Long payerShardNum, Long nodeAccountNum, Long nodeRealmNum,
      Long nodeShardNum, long transactionFee, Timestamp timestamp, Duration transactionDuration,
      boolean generateRecord, String memo) {
    AccountID payerAccountID = getAccountIdBuild(payerAccountNum, payerRealmNum, payerShardNum);
    AccountID nodeAccountID = getAccountIdBuild(nodeAccountNum, nodeRealmNum, nodeShardNum);
    return getTxBodyBuilder(transactionFee, timestamp, transactionDuration, generateRecord, memo,
        payerAccountID, nodeAccountID);
  }

  public static TransactionBody.Builder getTxBodyBuilder(long transactionFee, Timestamp timestamp,
      Duration transactionDuration, boolean generateRecord, String memo, AccountID payerAccountID,
      AccountID nodeAccountID) {
    TransactionID transactionID = getTransactionID(timestamp, payerAccountID);
    return TransactionBody.newBuilder().setTransactionID(transactionID)
        .setNodeAccountID(nodeAccountID)
        .setTransactionFee(transactionFee).setTransactionValidDuration(transactionDuration)
        .setGenerateRecord(generateRecord).setMemo(memo);
  }

  public static AccountID getAccountIdBuild(Long accountNum, Long realmNum, Long shardNum) {
    return AccountID.newBuilder().setAccountNum(accountNum).setRealmNum(realmNum)
        .setShardNum(shardNum).build();
  }

  public static FileID getFileIdBuild(Long accountNum, Long realmNum, Long shardNum) {
    return FileID.newBuilder().setFileNum(accountNum).setRealmNum(realmNum)
        .setShardNum(shardNum).build();
  }

  public static ContractID getContractIdBuild(Long accountNum, Long realmNum, Long shardNum) {
    return ContractID.newBuilder().setContractNum(accountNum).setRealmNum(realmNum)
        .setShardNum(shardNum).build();
  }

  public static TransactionID getTransactionID(Timestamp timestamp, AccountID payerAccountID) {
    return TransactionID.newBuilder().setAccountID(payerAccountID)
        .setTransactionValidStart(timestamp).build();
  }

  public static TransactionRecord.Builder getTransactionRecord(long txFee, String memo,
      TransactionID transactionID,
      Timestamp consensusTime, TransactionReceipt receipt) {
    return TransactionRecord.newBuilder().setConsensusTimestamp(consensusTime)
        .setTransactionID(transactionID)
        .setMemo(memo).setTransactionFee(txFee).setReceipt(receipt);
  }

  public static Timestamp getTimestamp(Instant instant) {
    return Timestamp.newBuilder().setNanos(instant.getNano()).setSeconds(instant.getEpochSecond())
        .build();
  }

  public static TimestampSeconds getTimestampSeconds(Instant instant) {
    return TimestampSeconds.newBuilder().setSeconds(instant.getEpochSecond())
        .build();
  }

  public static Duration getDuration(long seconds) {
    return Duration.newBuilder().setSeconds(seconds).build();
  }

  public static Query getCryptoGetInfoQuery(AccountID accountID, Transaction transaction,
      ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder().setResponseType(responseType)
        .setPayment(transaction)
        .build();
    return Query.newBuilder()
        .setCryptoGetInfo(
            CryptoGetInfoQuery.newBuilder().setAccountID(accountID).setHeader(queryHeader))
        .build();
  }

  public static Query getCryptoGetBalanceQuery(AccountID accountID, Transaction transaction,
      ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder().setResponseType(responseType)
        .setPayment(transaction)
        .build();
    return Query.newBuilder()
        .setCryptogetAccountBalance(
            CryptoGetAccountBalanceQuery.newBuilder().setAccountID(accountID)
                .setHeader(queryHeader))
        .build();
  }

  public static Query getFileContentQuery(FileID fileID, Transaction transaction,
      ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder().setResponseType(responseType)
        .setPayment(transaction)
        .build();
    return Query.newBuilder()
        .setFileGetContents(
            FileGetContentsQuery.newBuilder().setFileID(fileID).setHeader(queryHeader))
        .build();
  }

  public static Query getTransactionGetRecordQuery(TransactionID transactionID,
      Transaction transaction,
      ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder().setResponseType(responseType)
        .setPayment(transaction)
        .build();
    return Query.newBuilder()
        .setTransactionGetRecord(
            TransactionGetRecordQuery.newBuilder().setTransactionID(transactionID)
                .setHeader(queryHeader))
        .build();
  }

  public static Query getAccountRecordsQuery(AccountID accountID, Transaction transaction,
      ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder()
        .setResponseType(responseType)
        .setPayment(transaction)
        .build();
    return Query.newBuilder()
        .setCryptoGetAccountRecords(
            CryptoGetAccountRecordsQuery.newBuilder().setAccountID(accountID)
                .setHeader(queryHeader))
        .build();
  }

  public static Query getAccountLiveHashQuery(AccountID accountID, byte[] hash,
      Transaction transaction, ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder()
        .setResponseType(responseType)
        .setPayment(transaction)
        .build();
    return Query.newBuilder()
        .setCryptoGetLiveHash(CryptoGetLiveHashQuery.newBuilder()
                .setAccountID(accountID)
                .setHash(ByteString.copyFrom(hash))
                .setHeader(queryHeader))
        .build();
  }

  public static Query getContractRecordsQuery(ContractID contractID, Transaction transaction,
      ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder()
        .setResponseType(responseType)
        .setPayment(transaction)
        .build();
    return Query.newBuilder()
        .setContractGetRecords(
            ContractGetRecordsQuery.newBuilder().setContractID(contractID).setHeader(queryHeader))
        .build();
  }

  /**
   * Builds a file creation tx.
   *
   * @param fileData the content of the file
   * @param fileExpirationTime the expiration for the file
   * @param waclKeyList WACL keys, as List<ThresholdKeys>
   */
//  public static Transaction getFileCreateBuilder(Long payerAccountNum, Long payerRealmNum,
//      Long payerShardNum, Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
//      long transactionFee, Timestamp timestamp, Duration transactionDuration,
//      boolean generateRecord, String memo, SignatureList signatures, ByteString fileData,
//      Timestamp fileExpirationTime, List<Key> waclKeyList) {
//    FileCreateTransactionBody fileCreateTransactionBody = FileCreateTransactionBody.newBuilder()
//        .setExpirationTime(fileExpirationTime)
//        .setKeys(KeyList.newBuilder().addAllKeys(waclKeyList).build())
//        .setContents(fileData).build();
//
//    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
//        nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee, timestamp, transactionDuration,
//        generateRecord, memo);
//    body.setFileCreate(fileCreateTransactionBody);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }

  /**
   * Builds a file append tx.
   *
   * @param fileData file data to be appended
   * @param fileId file ID or hash of the tx that created the file
   */
//  public static Transaction getFileAppendBuilder(Long payerAccountNum, Long payerRealmNum,
//      Long payerShardNum,
//      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum, long transactionFee,
//      Timestamp timestamp,
//      Duration transactionDuration, boolean generateRecord, String memo, SignatureList signatures,
//      ByteString fileData, FileID fileId) {
//    FileAppendTransactionBody.Builder builder = FileAppendTransactionBody.newBuilder()
//        .setContents(fileData);
//    builder.setFileID(fileId);
//    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
//        nodeAccountNum,
//        nodeRealmNum, nodeShardNum, transactionFee, timestamp, transactionDuration, generateRecord,
//        memo);
//    body.setFileAppend(builder);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }

  /**
   * Builds a file update tx.
   */
//  public static Transaction getFileUpdateBuilder(Long payerAccountNum, Long payerRealmNum,
//      Long payerShardNum,
//      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
//      long transactionFee, Timestamp timestamp, Timestamp fileExpTime,
//      Duration transactionDuration, boolean generateRecord, String memo,
//      SignatureList signatures, ByteString data, FileID fid, KeyList keys) {
//    FileUpdateTransactionBody.Builder builder = FileUpdateTransactionBody.newBuilder()
//        .setContents(data)
//        .setFileID(fid).setExpirationTime(fileExpTime).setKeys(keys);
//
//    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
//        nodeAccountNum,
//        nodeRealmNum, nodeShardNum, transactionFee, timestamp, transactionDuration, generateRecord,
//        memo);
//    body.setFileUpdate(builder);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }

  /**
   * Builds a file update tx without of key update.
   */
//  public static Transaction getFileUpdateBuilder(Long payerAccountNum, Long payerRealmNum,
//                                                 Long payerShardNum,
//                                                 Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
//                                                 long transactionFee, Timestamp timestamp, Timestamp fileExpTime,
//                                                 Duration transactionDuration, boolean generateRecord, String memo,
//                                                 SignatureList signatures, ByteString data, FileID fid) {
//    FileUpdateTransactionBody.Builder builder = FileUpdateTransactionBody.newBuilder()
//            .setContents(data)
//            .setFileID(fid).setExpirationTime(fileExpTime);
//
//    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
//            nodeAccountNum,
//            nodeRealmNum, nodeShardNum, transactionFee, timestamp, transactionDuration, generateRecord,
//            memo);
//    body.setFileUpdate(builder);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }
  /**
   * Builds a file delete tx.
   *
   * @param fileID file ID
   */
//  public static Transaction getFileDeleteBuilder(Long payerAccountNum, Long payerRealmNum,
//      Long payerShardNum,
//      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
//      long transactionFee, Timestamp timestamp, Duration transactionDuration,
//      boolean generateRecord, String memo, SignatureList signatures,
//      FileID fileID) {
//    FileDeleteTransactionBody FileDeleteTransaction = FileDeleteTransactionBody.newBuilder()
//        .setFileID(fileID)
//        .build();
//    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
//        nodeAccountNum,
//        nodeRealmNum, nodeShardNum, transactionFee, timestamp,
//        transactionDuration, generateRecord, memo);
//    body.setFileDelete(FileDeleteTransaction);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }


  public static Query getFileGetContentBuilder(Transaction payment, FileID fileID,
      ResponseType responseType) {

    QueryHeader queryHeader = QueryHeader.newBuilder().setPayment(payment)
        .setResponseType(responseType).build();

    FileGetContentsQuery fileGetContentsQuery = FileGetContentsQuery.newBuilder()
        .setHeader(queryHeader)
        .setFileID(fileID).build();

    return Query.newBuilder().setFileGetContents(fileGetContentsQuery).build();
  }

  /**
   * getFileGetInfoBuilder
   */
  public static Query getFileGetInfoBuilder(Transaction payment, FileID fileID,
      ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder().setPayment(payment)
        .setResponseType(responseType).build();

    FileGetInfoQuery fileGetInfoQuery = FileGetInfoQuery.newBuilder().setHeader(queryHeader)
        .setFileID(fileID)
        .build();

    return Query.newBuilder().setFileGetInfo(fileGetInfoQuery).build();
  }

  /**
   * Builds FileGetInfo response.
   *
   * @param cost cost of answer if type is COST_ANSWER_VALUE, total cost of answer and state proof
   * if the type is COST_ANSWER_STATE_PROOF_VALUE
   * @return FileGetInfoResponse object
   */
  public static FileGetInfoResponse getFileGetInfoResponseBuilder(FileID fid, long size,
      Timestamp expTime,
      boolean deleted, List<Key> keys,
      ResponseCodeEnum precheckCode,
      ResponseType respType, long cost,
      ByteString stateProof) {
    Builder headerBuilder = ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(precheckCode)
        .setResponseType(respType);
    FileGetInfoResponse.FileInfo.Builder fileInfoBuilder = FileInfo.newBuilder();

    switch (respType.getNumber()) {
      case ResponseType.ANSWER_ONLY_VALUE:
        fileInfoBuilder.setFileID(fid).setSize(size)
            .setExpirationTime(expTime)
            .setDeleted(deleted)
            .setKeys(KeyList.newBuilder().addAllKeys(keys).build());
        break;

      case ResponseType.ANSWER_STATE_PROOF_VALUE:
        headerBuilder.setStateProof(stateProof);
        fileInfoBuilder.setFileID(fid).setSize(size)
            .setExpirationTime(expTime)
            .setDeleted(deleted)
            .setKeys(KeyList.newBuilder().addAllKeys(keys).build());
        break;

      case ResponseType.COST_ANSWER_STATE_PROOF_VALUE:
        headerBuilder.setCost(cost);
        break;

      case ResponseType.COST_ANSWER_VALUE:
        headerBuilder.setCost(cost);
        break;

      default:
        break;
    }

    return FileGetInfoResponse.newBuilder().setHeader(headerBuilder.build())
        .setFileInfo(fileInfoBuilder.build())
        .build();
  }

  public static Timestamp getExpirationTime(Instant startTime, Duration autoRenewalTime) {
    Instant autoRenewPeriod = startTime.plusSeconds(autoRenewalTime.getSeconds());

    return getTimestamp(autoRenewPeriod);
  }

  public static Instant convertProtoTimeStamp(Timestamp timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }

  public static Instant convertProtoTimeStampSeconds(TimestampSeconds timestampSeconds) {
    return Instant.ofEpochSecond(timestampSeconds.getSeconds());
  }

  public static Instant convertProtoDuration(Duration timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), 0);
  }

  public static ResponseHeader getResponseHeader(ResponseCodeEnum code, long cost,
      ResponseType type,
      ByteString stateProof) {
    return ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(code).setCost(cost)
        .setResponseType(type)
        .setStateProof(stateProof).build();
  }

  public static ResponseHeader getResponseHeaderNew(ResponseCodeEnum code, Long cost,
      ResponseType type, ByteString stateProof) {
    Builder builder = ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(code).setResponseType(type);
    if(cost != null)
      builder.setCost(cost);
    if(stateProof != null)
      builder.setStateProof(stateProof).build();

    return builder.build();
  }

//  public static Transaction getCreateContractRequest(Long payerAccountNum, Long payerRealmNum,
//      Long payerShardNum,
//      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
//      long transactionFee, Timestamp timestamp, Duration txDuration,
//      boolean generateRecord, String txMemo, long gas, FileID fileId,
//      ByteString constructorParameters, long initialBalance,
//      Duration autoRenewalPeriod, SignatureList signatures, String contractMemo) {
//    return getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum,
//        nodeAccountNum, nodeRealmNum, nodeShardNum,
//        transactionFee, timestamp, txDuration,
//        generateRecord, txMemo, gas, fileId,
//        constructorParameters, initialBalance,
//        autoRenewalPeriod, signatures, contractMemo, null);
//  }


//  public static Transaction getCreateContractRequest(Long payerAccountNum, Long payerRealmNum,
//      Long payerShardNum,
//      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
//      long transactionFee, Timestamp timestamp, Duration txDuration,
//      boolean generateRecord, String txMemo, long gas, FileID fileId,
//      ByteString constructorParameters, long initialBalance,
//      Duration autoRenewalPeriod, SignatureList signatures, String contractMemo,
//      Key adminKey) {
//
//    ContractCreateTransactionBody.Builder contractCreateInstance = ContractCreateTransactionBody
//        .newBuilder()
//        .setGas(gas).setProxyAccountID(getAccountIdBuild(0L, 0L, 0L))
//        .setAutoRenewPeriod(autoRenewalPeriod);
//    if (fileId != null && fileId.isInitialized()) {
//      contractCreateInstance = contractCreateInstance.setFileID(fileId);
//    }
//
//    if (constructorParameters != null) {
//      contractCreateInstance = contractCreateInstance
//          .setConstructorParameters(constructorParameters);
//    }
//    if (initialBalance != 0) {
//      contractCreateInstance = contractCreateInstance.setInitialBalance(initialBalance);
//    }
//
//    if (!Strings.isNullOrEmpty(contractMemo)) {
//      contractCreateInstance = contractCreateInstance.setMemo(contractMemo);
//    }
//
//    if (adminKey != null) {
//      contractCreateInstance = contractCreateInstance.setAdminKey(adminKey);
//    }
//    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
//        nodeAccountNum,
//        nodeRealmNum, nodeShardNum, transactionFee, timestamp,
//        txDuration, generateRecord, txMemo);
//    body.setContractCreateInstance(contractCreateInstance);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }

//  public static Transaction getCryptoTransferRequest(Long payerAccountNum, Long payerRealmNum,
//      Long payerShardNum,
//      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
//      long transactionFee, Timestamp timestamp,
//      Duration transactionDuration, boolean generateRecord, String memo,
//      SignatureList signatures, Long senderActNum, Long amountSend,
//      Long receiverAcctNum, Long amountRecieved) {
//
//    AccountAmount a1 = AccountAmount.newBuilder()
//        .setAccountID(getAccountIdBuild(senderActNum, 0l, 0l))
//        .setAmount(amountSend).build();
//    AccountAmount a2 = AccountAmount.newBuilder()
//        .setAccountID(getAccountIdBuild(receiverAcctNum, 0l, 0l))
//        .setAmount(amountRecieved).build();
//    TransferList transferList = TransferList.newBuilder().addAccountAmounts(a1)
//        .addAccountAmounts(a2).build();
//    CryptoTransferTransactionBody cryptoTransferTransaction = CryptoTransferTransactionBody
//        .newBuilder()
//        .setTransfers(transferList).build();
//
//    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
//        nodeAccountNum,
//        nodeRealmNum, nodeShardNum, transactionFee, timestamp,
//        transactionDuration, generateRecord, memo);
//    body.setCryptoTransfer(cryptoTransferTransaction);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//
//  }

//  public static Transaction getCryptoTransferRequest(Long payerAccountNum, Long payerRealmNum,
//          Long payerShardNum,
//          Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
//          long transactionFee, Timestamp timestamp,
//          Duration transactionDuration, boolean generateRecord, String memo,
//          SignatureList signatures, TransferList transferList) {
//    CryptoTransferTransactionBody cryptoTransferTransaction = CryptoTransferTransactionBody
//            .newBuilder()
//            .setTransfers(transferList).build();
//
//    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
//            nodeAccountNum,
//            nodeRealmNum, nodeShardNum, transactionFee, timestamp,
//            transactionDuration, generateRecord, memo);
//    body.setCryptoTransfer(cryptoTransferTransaction);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }

  public static TransactionGetReceiptQuery getTransactionGetReceiptQuery(
      TransactionID transactionID,
      ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder().setResponseType(responseType).build();
    return TransactionGetReceiptQuery.newBuilder().setHeader(queryHeader)
        .setTransactionID(transactionID).build();
  }

  public static TransactionGetFastRecordQuery getFastTransactionRecordQuery(
      TransactionID transactionID,
      ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder().setResponseType(responseType).build();
    return TransactionGetFastRecordQuery.newBuilder().setHeader(queryHeader)
        .setTransactionID(transactionID).build();
  }

  public static LiveHash getLiveHash(
          AccountID accountIdBuild,
          Duration transactionDuration,
          KeyList keyList,
          byte[] hash
  ) {
    return LiveHash.newBuilder().setAccountId(accountIdBuild).setHash(ByteString.copyFrom(hash))
        .setDuration(transactionDuration).setKeys(keyList).build();
  }

//  public static Transaction getContractCallRequest(Long payerAccountNum, Long payerRealmNum,
//      Long payerShardNum,
//      Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
//      long transactionFee, Timestamp timestamp,
//      Duration txDuration, long gas, ContractID contractId,
//      ByteString functionData, long value,
//      SignatureList signatures) {
//    ContractCallTransactionBody.Builder contractCall = ContractCallTransactionBody.newBuilder()
//        .setContractID(contractId).setGas(gas).setFunctionParameters(functionData).setAmount(value);
//
//    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
//        nodeAccountNum,
//        nodeRealmNum, nodeShardNum, transactionFee, timestamp,
//        txDuration, true, "");
//    body.setContractCall(contractCall);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }

  public static Query getContractCallLocalQuery(ContractID contractId, long gas,
      ByteString functionData, long value,
      long maxResultSize, Transaction transaction, ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder().setResponseType(responseType)
        .setPayment(transaction)
        .build();
    return Query.newBuilder()
        .setContractCallLocal(
            ContractCallLocalQuery.newBuilder().setContractID(contractId).setGas(gas)
                .setFunctionParameters(functionData).setMaxResultSize(maxResultSize)
                .setHeader(queryHeader))
        .build();
  }

  public static TransactionReceipt getTransactionReceipt(AccountID accountID,
      ResponseCodeEnum status, ExchangeRateSet exchangeRateSet) {
    return TransactionReceipt.newBuilder()
        .setAccountID(accountID)
        .setStatus(status)
        .setExchangeRate(exchangeRateSet)
        .build();
  }

  public static TransactionReceipt getTransactionReceipt(ResponseCodeEnum status,
      ExchangeRateSet exchangeRateSet) {
    return TransactionReceipt.newBuilder()
        .setStatus(status)
        .setExchangeRate(exchangeRateSet)
        .build();
  }

  public static TransactionReceipt getTransactionReceipt(FileID fileID, ResponseCodeEnum status,
      ExchangeRateSet exchangeRateSet) {
    return TransactionReceipt.newBuilder()
        .setFileID(fileID)
        .setStatus(status)
        .setExchangeRate(exchangeRateSet)
        .build();
  }

  public static TransactionReceipt getTransactionReceipt(ContractID contractID,
      ResponseCodeEnum status, ExchangeRateSet exchangeRateSet) {
    return TransactionReceipt.newBuilder()
        .setContractID(contractID)
        .setStatus(status)
        .setExchangeRate(exchangeRateSet)
        .build();
  }

  public static TransactionReceipt getTransactionReceipt(ResponseCodeEnum status) {
    return TransactionReceipt.newBuilder().setStatus(status).build();
  }

  public static Query getContractGetInfoQuery(ContractID contractId, Transaction transaction,
      ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder().setResponseType(responseType)
        .setPayment(transaction)
        .build();
    return Query.newBuilder()
        .setContractGetInfo(ContractGetInfoQuery.newBuilder().setContractID(contractId)
            .setHeader(queryHeader)).build();

  }

  public static Query getContractGetBytecodeQuery(ContractID contractId, Transaction transaction,
      ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder().setResponseType(responseType)
        .setPayment(transaction)
        .build();
    return Query.newBuilder().setContractGetBytecode(ContractGetBytecodeQuery.newBuilder()
        .setContractID(contractId)
        .setHeader(queryHeader))
        .build();

  }

//  public static Transaction getContractUpdateRequestWrapper(AccountID payerAccount,
//      AccountID nodeAccount,
//      long transactionFee, Timestamp startTime,
//      Duration transactionDuration, boolean generateRecord, String memo,
//      ContractID contractId, Duration autoRenewPeriod,
//      List<Key> adminKeysList, AccountID proxyAccount,
//      Timestamp expirationTime, SignatureList signatures, String contractMemo) {
//
//    Key adminKey = null;
//    if (adminKeysList != null && !adminKeysList.isEmpty()) {
//      adminKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(adminKeysList).build())
//          .build();
//    }
//
//    return getContractUpdateRequest(payerAccount, nodeAccount,
//        transactionFee, startTime,
//        transactionDuration, generateRecord, memo,
//        contractId, autoRenewPeriod,
//        adminKey, proxyAccount,
//        expirationTime, signatures, contractMemo);
//  }

//  public static Transaction getContractUpdateRequest(AccountID payerAccount, AccountID nodeAccount,
//      long transactionFee, Timestamp startTime,
//      Duration transactionDuration, boolean generateRecord, String memo,
//      ContractID contractId, Duration autoRenewPeriod,
//      Key adminKey, AccountID proxyAccount,
//      Timestamp expirationTime, SignatureList signatures, String contractMemo) {
//
//    ContractUpdateTransactionBody.Builder contractUpdateBld = ContractUpdateTransactionBody
//        .newBuilder();
//
//    contractUpdateBld = contractUpdateBld.setContractID(contractId);
//    if (autoRenewPeriod != null && autoRenewPeriod.isInitialized()) {
//      contractUpdateBld = contractUpdateBld.setAutoRenewPeriod(autoRenewPeriod);
//    }
//
//    if (adminKey != null) {
//      contractUpdateBld = contractUpdateBld.setAdminKey(adminKey);
//    }
//
//    if (proxyAccount != null && proxyAccount.isInitialized()) {
//      contractUpdateBld = contractUpdateBld.setProxyAccountID(proxyAccount);
//    }
//
//    if (expirationTime != null && expirationTime.isInitialized()) {
//      contractUpdateBld = contractUpdateBld.setExpirationTime(expirationTime);
//    }
//    if (!Strings.isNullOrEmpty(contractMemo)) {
//      contractUpdateBld = contractUpdateBld.setMemo(contractMemo);
//    }
//
//    TransactionBody.Builder body = getTransactionBody(payerAccount.getAccountNum(),
//        payerAccount.getRealmNum(),
//        payerAccount.getShardNum(), nodeAccount.getAccountNum(),
//        nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
//        transactionFee, startTime, transactionDuration, generateRecord,
//        memo);
//    body.setContractUpdateInstance(contractUpdateBld);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }

  public static Query getBySolidityIDQuery(String solidityID, Transaction transaction,
      ResponseType responseType) {
    QueryHeader queryHeader = QueryHeader.newBuilder().setResponseType(responseType)
        .setPayment(transaction)
        .build();
    return Query.newBuilder()
        .setGetBySolidityID(GetBySolidityIDQuery.newBuilder().setSolidityID(solidityID)
            .setHeader(queryHeader)).build();

  }


//  public static Transaction getCreateAccountBuilder(Long payerAccountNum, Long payerRealmNum,
//      Long payerShardNum, Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
//      long transactionFee, Timestamp startTime, Duration transactionDuration,
//      boolean generateRecord, String memo, Key key, long initBal, long sendRecordThreshold,
//      long receiveRecordThreshold, boolean receiverSign,
//      Duration autoRenew, SignatureList signatures) {
//    CryptoCreateTransactionBody createAccount = CryptoCreateTransactionBody.newBuilder().setKey(key)
//        .setInitialBalance(initBal).setProxyAccountID(getAccountIdBuild(0L, 0L, 0L))
//        .setReceiveRecordThreshold(receiveRecordThreshold)
//        .setSendRecordThreshold(sendRecordThreshold).setReceiverSigRequired(receiverSign)
//        .setAutoRenewPeriod(autoRenew).build();
//
//    TransactionBody.Builder body = getTransactionBody(payerAccountNum, payerRealmNum, payerShardNum,
//        nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee, startTime,
//        transactionDuration, generateRecord, memo);
//    body.setCryptoCreateAccount(createAccount);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }

  public static ExchangeRate getExchangeRateBuilder(int hbarEquivalent, int centEquivalent,
      long expirationSeconds) {
    return ExchangeRate.newBuilder()
        .setHbarEquiv(hbarEquivalent)
        .setCentEquiv(centEquivalent)
        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(expirationSeconds).build())
        .build();
  }

  public static ExchangeRateSet getExchangeRateSetBuilder(int currentHbarEquivalent,
      int currentCentEquivalent, long currentExpirationSeconds, int nextHbarEquivalent,
      int nextCentEquivalent, long nextExpirationSeconds) {
    return ExchangeRateSet.newBuilder()
        .setCurrentRate(getExchangeRateBuilder(currentHbarEquivalent, currentCentEquivalent,
            currentExpirationSeconds))
        .setNextRate(getExchangeRateBuilder(nextHbarEquivalent, nextCentEquivalent,
            nextExpirationSeconds))
        .build();
  }

  public static SystemDeleteTransactionBody getSystemDeleteTransactionBody(FileID fileID,
      long expireTimeInSeconds) {
    return SystemDeleteTransactionBody.newBuilder()
        .setFileID(fileID)
        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(expireTimeInSeconds).build())
        .build();
  }

//  public static Transaction getDeleteContractRequest(AccountID payer,
//      AccountID node,
//      long transactionFee, Timestamp timestamp, Duration txDuration, ContractID contractId,
//      AccountID transferAccount,
//      ContractID transferContract,
//      boolean generateRecord, String txMemo, SignatureList signatures) {
//    ContractDeleteTransactionBody.Builder contractDeleteBuilder = ContractDeleteTransactionBody
//        .newBuilder();
//    contractDeleteBuilder = contractDeleteBuilder.setContractID(contractId);
//    if (transferAccount != null) {
//      contractDeleteBuilder.setTransferAccountID(transferAccount);
//    } else if (transferContract != null) {
//      contractDeleteBuilder.setTransferContractID(transferContract);
//    }
//    ContractDeleteTransactionBody contractDeleteBody = contractDeleteBuilder.build();
//    TransactionBody.Builder body = getTransactionBody(payer.getAccountNum(), payer.getRealmNum(),
//        payer.getShardNum(),
//        node.getAccountNum(),
//        node.getRealmNum(), node.getShardNum(), transactionFee, timestamp,
//        txDuration, generateRecord, txMemo);
//    body.setContractDeleteInstance(contractDeleteBody);
//    byte[] bodyBytesArr = body.build().toByteArray();
//    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
//    return Transaction.newBuilder().setBodyBytes(bodyBytes).setSigs(signatures).build();
//  }
}
