package com.hedera.services.legacy.core;

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

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public class TxnValidityAndFeeReq {
  private static final long DEFAULT_COST_VALUE = 0;
  private ResponseCodeEnum validity;
  private long feeRequired;

  public TxnValidityAndFeeReq(ResponseCodeEnum _status) {
    this.validity = _status;
    this.feeRequired = DEFAULT_COST_VALUE;
  }

  public TxnValidityAndFeeReq(ResponseCodeEnum _status, long _cost) {
    this.validity = _status;
    this.feeRequired = _cost;
  }


  public ResponseCodeEnum getValidity() {
    return validity;
  }

  public long getFeeRequired() {
    return feeRequired;
  }

  @Override
  public String toString() {
    return "XactionResponse{" +
        "status=" + validity +
        ", cost=" + feeRequired +
        '}';
  }
}
