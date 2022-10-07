/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.domain.process;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public final class TxnValidityAndFeeReq {
    private static final long NO_REQUIRED_FEE = 0;

    private long requiredFee = NO_REQUIRED_FEE;
    private final ResponseCodeEnum validity;

    public TxnValidityAndFeeReq(final ResponseCodeEnum validity) {
        this.validity = validity;
    }

    public TxnValidityAndFeeReq(final ResponseCodeEnum validity, final long requiredFee) {
        this.validity = validity;
        this.requiredFee = requiredFee;
    }

    public ResponseCodeEnum getValidity() {
        return validity;
    }

    public long getRequiredFee() {
        return requiredFee;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("validity", validity)
                .add("requiredFee", requiredFee)
                .toString();
    }
}
