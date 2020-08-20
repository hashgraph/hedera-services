package com.hedera.services.txns.validation;

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

import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.time.Clock;
import java.time.Instant;

import static com.hedera.services.txns.validation.PureValidation.asCoercedInstant;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;

public class BasicPrecheck {
	private final PropertySource properties;
	private final OptionValidator validator;

	public BasicPrecheck(PropertySource properties, OptionValidator validator) {
		this.properties = properties;
		this.validator = validator;
	}

	public ResponseCodeEnum validate(TransactionBody txn) {
		if (!txn.hasTransactionID()) {
			return INVALID_TRANSACTION_ID;
		}
		if (!validator.isPlausibleTxnFee(txn.getTransactionFee())) {
			return INSUFFICIENT_TX_FEE;
		}
		if (!validator.isPlausibleAccount(txn.getTransactionID().getAccountID())) {
			return PAYER_ACCOUNT_NOT_FOUND;
		}
		if (!validator.isPlausibleAccount(txn.getNodeAccountID())) {
			return INVALID_NODE_ACCOUNT;
		}
		if (!validator.isValidEntityMemo(txn.getMemo())) {
			return MEMO_TOO_LONG;
		}

		var validForSecs = txn.getTransactionValidDuration().getSeconds();
		if (!validator.isValidTxnDuration(validForSecs)) {
			return INVALID_TRANSACTION_DURATION;
		}

		return validator.chronologyStatusForTxn(
				asCoercedInstant(txn.getTransactionID().getTransactionValidStart()),
				validForSecs - properties.getIntProperty("hedera.transaction.minValidityBufferSecs"),
				Instant.now(Clock.systemUTC()));
	}
}
