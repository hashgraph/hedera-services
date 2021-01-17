package com.hedera.services.legacy.crypto;

/*-
 * ‌
 * Hedera Services Node
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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.ArrayList;

import static com.hedera.services.utils.EntityIdUtils.readableId;

public class SignatureStatus {
  private SignatureStatusCode statusCode;
  private ResponseCodeEnum responseCode;
  private boolean handlingTransaction;
  private TransactionID transactionId;
  private AccountID accountId;
  private FileID fileId;
  private ContractID contractId;
  private TopicID topicId;
  private TokenID tokenId;
  private ScheduleID scheduleID;
  private TransactionBody scheduled;
  private SignatureStatus errorReport;

  public SignatureStatus(final SignatureStatusCode statusCode, final ResponseCodeEnum responseCode,
      final boolean handlingTransaction, final TransactionID transactionID,
      final AccountID accountId,
      final FileID fileId, final ContractID contractId, final TopicID topicId) {
    this.statusCode = statusCode;
    this.responseCode = responseCode;
    this.handlingTransaction = handlingTransaction;
    this.transactionId = transactionID;
    this.accountId = accountId;
    this.fileId = fileId;
    this.contractId = contractId;
    this.topicId = topicId;
  }

  public SignatureStatus(final SignatureStatusCode statusCode, final ResponseCodeEnum responseCode,
          final boolean handlingTransaction, final TransactionID transactionID,
          final TokenID tokenID) {
    this.statusCode = statusCode;
    this.responseCode = responseCode;
    this.handlingTransaction = handlingTransaction;
    this.transactionId = transactionID;
    this.tokenId = tokenID;
  }

  public SignatureStatus(final SignatureStatusCode statusCode, final ResponseCodeEnum responseCode,
                         final boolean handlingTransaction, final TransactionID transactionID,
                         final ScheduleID scheduleID) {
    this.statusCode = statusCode;
    this.responseCode = responseCode;
    this.handlingTransaction = handlingTransaction;
    this.transactionId = transactionID;
    this.scheduleID = scheduleID;
  }

  public SignatureStatus(final SignatureStatusCode statusCode, final ResponseCodeEnum responseCode,
          final boolean handlingTransaction, final TransactionID transactionID) {
    this.statusCode = statusCode;
    this.responseCode = responseCode;
    this.handlingTransaction = handlingTransaction;
    this.transactionId = transactionID;
  }

  public SignatureStatus(
          SignatureStatusCode statusCode,
          ResponseCodeEnum responseCode,
          boolean handlingTransaction,
          TransactionID transactionID,
          TransactionBody scheduled,
          SignatureStatus errorReport
  ) {
    this.scheduled = scheduled;
    this.statusCode = statusCode;
    this.responseCode = responseCode;
    this.handlingTransaction = handlingTransaction;
    this.transactionId = transactionID;
    this.errorReport = errorReport;
  }

  public ResponseCodeEnum getResponseCode() {
    return responseCode;
  }

  public SignatureStatusCode getStatusCode() {
    return statusCode;
  }

  public AccountID getAccountId() {
    return accountId;
  }

  public boolean hasAccountId() {
    return accountId != null && accountId.isInitialized();
  }

  public TopicID getTopicId() {
    return topicId;
  }

  public boolean isError() {
    return !SignatureStatusCode.SUCCESS.equals(statusCode) &&
        !SignatureStatusCode.SUCCESS_VERIFY_SYNC.equals(statusCode) &&
        !SignatureStatusCode.SUCCESS_VERIFY_ASYNC.equals(statusCode);
  }

  @Override
  public String toString() {
    return toLogMessage();
  }

  public String toLogMessage() {
    final ArrayList<String> formatArguments = new ArrayList<>();
    formatArguments.add((handlingTransaction) ? "handleTransaction" : "expandSignatures");

    switch (statusCode) {
      case VERIFY_FAILED:
      case INVALID_ACCOUNT_ID:
      case INVALID_AUTO_RENEW_ACCOUNT_ID:
        formatArguments.add(format(transactionId));
        formatArguments.add(readableId(accountId));
        break;
      case INVALID_FILE_ID:
        formatArguments.add(format(transactionId));
        formatArguments.add(readableId(fileId));
        break;
      case INVALID_CONTRACT_ID:
      case IMMUTABLE_CONTRACT:
        formatArguments.add(format(transactionId));
        formatArguments.add(readableId(contractId));
        break;
	  case UNRESOLVABLE_REQUIRED_SIGNERS:
		formatArguments.add(scheduled.toString());
		formatArguments.add(errorReport.getResponseCode().toString());
		break;
      case UNPARSEABLE_SCHEDULED_TRANSACTION:
      case GENERAL_PAYER_ERROR:
      case GENERAL_TRANSACTION_ERROR:
      case KEY_COUNT_MISMATCH:
      case KEY_PREFIX_MISMATCH:
        formatArguments.add(format(transactionId));
        break;
      case SUCCESS_VERIFY_SYNC:
        formatArguments.add(Boolean.FALSE.toString());
        break;
      case SUCCESS_VERIFY_ASYNC:
        formatArguments.add(Boolean.TRUE.toString());
        break;
      case INVALID_TOPIC_ID:
        formatArguments.add(format(transactionId));
        formatArguments.add(readableId(topicId));
        break;
      case INVALID_TOKEN_ID:
        formatArguments.add(format(transactionId));
        formatArguments.add(readableId(tokenId));
        break;
      case INVALID_SCHEDULE_ID:
        formatArguments.add(format(transactionId));
        formatArguments.add(readableId(scheduleID));
        break;
      case SUCCESS:
      case INVALID_PROTOCOL_BUFFER:
      case GENERAL_ERROR:
      default:
        break;
    }

    return String.format(statusCode.message(), formatArguments.toArray());
  }

  static String format(final TransactionID transactionId) {
    return String.format("(%s, %d.%d)", readableId(transactionId.getAccountID()),
        transactionId.getTransactionValidStart().getSeconds(),
        transactionId.getTransactionValidStart().getNanos());
  }
}
