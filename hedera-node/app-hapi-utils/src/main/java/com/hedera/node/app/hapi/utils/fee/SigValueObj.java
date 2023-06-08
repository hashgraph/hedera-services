/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.utils.fee;

public class SigValueObj {

    public SigValueObj(int totalSigCount, int payerAcctSigCount, int signatureSize) {
        super();
        this.totalSigCount = totalSigCount;
        this.payerAcctSigCount = payerAcctSigCount;
        this.signatureSize = signatureSize;
    }

    private int totalSigCount;
    private int payerAcctSigCount;
    private int signatureSize;

    public int getTotalSigCount() {
        return totalSigCount;
    }

    public int getPayerAcctSigCount() {
        return payerAcctSigCount;
    }

    public int getSignatureSize() {
        return signatureSize;
    }
}
