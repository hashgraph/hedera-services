package com.hedera.services.txns.submission;

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

import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.tuple.Pair;

import java.util.EnumMap;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS;

/**
 * Error response factory that caches well-known responses by status code.
 */
class PresolvencyFlaws {
	private static Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> failureWithUnknownFeeReq(
			ResponseCodeEnum error
	) {
		return Pair.of(new TxnValidityAndFeeReq(error), Optional.empty());
	}

	static final EnumMap<ResponseCodeEnum, Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>>> WELL_KNOWN_FLAWS =
			new EnumMap<>(ResponseCodeEnum.class) {{
				put(PLATFORM_NOT_ACTIVE, failureWithUnknownFeeReq(PLATFORM_NOT_ACTIVE));
				/* Structural */
				put(INVALID_TRANSACTION, failureWithUnknownFeeReq(INVALID_TRANSACTION));
				put(TRANSACTION_OVERSIZE, failureWithUnknownFeeReq(TRANSACTION_OVERSIZE));
				put(INVALID_TRANSACTION_BODY, failureWithUnknownFeeReq(INVALID_TRANSACTION_BODY));
				put(TRANSACTION_TOO_MANY_LAYERS, failureWithUnknownFeeReq(TRANSACTION_TOO_MANY_LAYERS));
				/* Syntactic */
				put(INVALID_TRANSACTION_ID, failureWithUnknownFeeReq(INVALID_TRANSACTION_ID));
				put(TRANSACTION_ID_FIELD_NOT_ALLOWED, failureWithUnknownFeeReq(TRANSACTION_ID_FIELD_NOT_ALLOWED));
				put(INSUFFICIENT_TX_FEE, failureWithUnknownFeeReq(INSUFFICIENT_TX_FEE));
				put(PAYER_ACCOUNT_NOT_FOUND, failureWithUnknownFeeReq(PAYER_ACCOUNT_NOT_FOUND));
				put(INVALID_NODE_ACCOUNT, failureWithUnknownFeeReq(INVALID_NODE_ACCOUNT));
				put(MEMO_TOO_LONG, failureWithUnknownFeeReq(MEMO_TOO_LONG));
				put(INVALID_ZERO_BYTE_IN_STRING, failureWithUnknownFeeReq(INVALID_ZERO_BYTE_IN_STRING));
				put(INVALID_TRANSACTION_DURATION, failureWithUnknownFeeReq(INVALID_TRANSACTION_DURATION));
				put(INVALID_TRANSACTION_START, failureWithUnknownFeeReq(INVALID_TRANSACTION_START));
				put(TRANSACTION_EXPIRED, failureWithUnknownFeeReq(TRANSACTION_EXPIRED));
				put(DUPLICATE_TRANSACTION, failureWithUnknownFeeReq(DUPLICATE_TRANSACTION));
			}};

	static Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> responseForFlawed(ResponseCodeEnum status) {
		Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> response;
		return (response = WELL_KNOWN_FLAWS.get(status)) != null
				? response
				: Pair.of(new TxnValidityAndFeeReq(status), Optional.empty());
	}
}
