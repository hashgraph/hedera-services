package com.hedera.services.legacy.crypto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

public enum SignatureStatusCode {
  SUCCESS("Successfully mapped signatures to keys [ source = '%s' ]"),
  SUCCESS_VERIFY_SYNC("Successfully mapped signatures to keys [ source = '%s', async = '%s' ]"),
  SUCCESS_VERIFY_ASYNC("Successfully mapped signatures to keys [ source = '%s', async = '%s'  ]"),
  INVALID_PROTOCOL_BUFFER("Unable to parse the platform transaction [ source = '%s' ]"),
  INVALID_ACCOUNT_ID(
      "Invalid Account ID [ source = '%s', transactionId = '%s',  accountId = '%s' ]"),
  GENERAL_ERROR("Unable to map signatures due to an general exception [ source = '%s' ]"),
  GENERAL_TRANSACTION_ERROR(
      "Unable to map signatures due to an general exception [ source = '%s', transactionId = '%s' ]"),
  GENERAL_PAYER_ERROR(
      "Unable to map signatures due to an general payer exception [ source = '%s', transactionId = '%s' ]"),
  KEY_COUNT_MISMATCH("Key Count Mismatch Error [ source = '%s', transactionId = '%s' ]"),
  KEY_PREFIX_MISMATCH("Key Prefix Mismatch Error [ source = '%s', transactionId = '%s' ]"),
  INVALID_FILE_ID("Invalid Account ID [ source = '%s', transactionId = '%s', fileId = '%s' ]"),
  IMMUTABLE_CONTRACT(
      "Immutable Contract Modification Error [ source = '%s', transactionId = '%s', contractId = '%s' ]"),
  INVALID_CONTRACT_ID(
      "Invalid Contract ID [ source = '%s', transactionId = '%s', contractId = '%s' ]"),
  VERIFY_FAILED(
      "Failed to verify signature [ source = '%s', transactionId = '%s', accountId = '%s' ]"),
  INVALID_TOPIC_ID("Invalid Topic ID [ source = '%s', transactionId = '%s', topicId = '%s' ]"),
  INVALID_TOKEN_ID("Invalid Token ID [ source = '%s', transactionId = '%s', tokenId = '%s' ]"),
  INVALID_AUTO_RENEW_ACCOUNT_ID("Invalid AutoRenew Account ID [ source = '%s', transactionId = '%s', accountId = '%s' ]"),
  INVALID_SCHEDULE_ID("Invalid Schedule ID [ source = '%s', transactionId = '%s', scheduleId = '%s' ]"),
  UNRESOLVABLE_REQUIRED_SIGNERS(
          "Cannot resolve required signers for scheduled txn [ source = '%s', scheduled = '%s', error = '%s' ]"),
  UNPARSEABLE_SCHEDULED_TRANSACTION(
          "Cannot parse scheduled txn [ source = '%s', transactionId = '%s' ]"),
  SCHEDULED_TRANSACTION_NOT_IN_WHITELIST(
          "Specified txn cannot be scheduled [ source = '%s', transactionId = '%s' ]");

  private String message;

  SignatureStatusCode(final String message) {
    this.message = message;
  }

  public String message() {
    return this.message;
  }
}
