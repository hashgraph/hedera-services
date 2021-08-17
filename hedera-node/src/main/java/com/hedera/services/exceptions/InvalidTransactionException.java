package com.hedera.services.exceptions;

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

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

/**
 * Captures a failure in transaction processing to be captured by the {@link
 * com.hedera.services.txns.TransitionRunner} and used to set the final resolved status of the
 * transaction.
 *
 * <p>Unless the contained {@link ResponseCodeEnum} is exactly {@code FAIL_INVALID}, this represents
 * some form of user error. The {@code FAIL_INVALID} code indicates an internal system error; and it
 * is usually desirable in that case to include a detail message in the constructor.
 */
public class InvalidTransactionException extends RuntimeException {
  private final ResponseCodeEnum responseCode;

  public InvalidTransactionException(ResponseCodeEnum responseCode) {
    this.responseCode = responseCode;
  }

  public InvalidTransactionException(String msg, ResponseCodeEnum responseCode) {
    super(msg);
    this.responseCode = responseCode;
  }

  public ResponseCodeEnum getResponseCode() {
    return responseCode;
  }
}
