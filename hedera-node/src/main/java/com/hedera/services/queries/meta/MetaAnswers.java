package com.hedera.services.queries.meta;

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

public class MetaAnswers {
	private final GetTxnRecordAnswer txnRecord;
	private final GetTxnReceiptAnswer txnReceipt;
	private final GetVersionInfoAnswer versionInfo;
	private final GetFastTxnRecordAnswer fastTxnRecord;

	public MetaAnswers(
			GetTxnRecordAnswer txnRecord,
			GetTxnReceiptAnswer txnReceipt,
			GetVersionInfoAnswer versionInfo,
			GetFastTxnRecordAnswer fastTxnRecord
	) {
		this.txnRecord = txnRecord;
		this.txnReceipt = txnReceipt;
		this.versionInfo = versionInfo;
		this.fastTxnRecord = fastTxnRecord;
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
}
