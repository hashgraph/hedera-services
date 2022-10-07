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
package com.hedera.services.queries.meta;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MetaAnswers {
    private final GetExecTimeAnswer execTime;
    private final GetTxnRecordAnswer txnRecord;
    private final GetTxnReceiptAnswer txnReceipt;
    private final GetVersionInfoAnswer versionInfo;
    private final GetFastTxnRecordAnswer fastTxnRecord;
    private final GetAccountDetailsAnswer accountDetails;

    @Inject
    public MetaAnswers(
            GetExecTimeAnswer execTime,
            GetTxnRecordAnswer txnRecord,
            GetTxnReceiptAnswer txnReceipt,
            GetVersionInfoAnswer versionInfo,
            GetFastTxnRecordAnswer fastTxnRecord,
            GetAccountDetailsAnswer accountDetails) {
        this.execTime = execTime;
        this.txnRecord = txnRecord;
        this.txnReceipt = txnReceipt;
        this.versionInfo = versionInfo;
        this.fastTxnRecord = fastTxnRecord;
        this.accountDetails = accountDetails;
    }

    public GetVersionInfoAnswer getVersionInfo() {
        return versionInfo;
    }

    public GetTxnReceiptAnswer getTxnReceipt() {
        return txnReceipt;
    }

    public GetTxnRecordAnswer getTxnRecord() {
        return txnRecord;
    }

    public GetFastTxnRecordAnswer getFastTxnRecord() {
        return fastTxnRecord;
    }

    public GetExecTimeAnswer getExecTime() {
        return execTime;
    }

    public GetAccountDetailsAnswer getAccountDetails() {
        return accountDetails;
    }
}
