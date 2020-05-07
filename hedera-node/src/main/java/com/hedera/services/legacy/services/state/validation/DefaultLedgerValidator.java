package com.hedera.services.legacy.services.state.validation;

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

import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.state.validation.LedgerValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.swirlds.fcmap.FCMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOTAL_LEDGER_BALANCE_INVALID;

public class DefaultLedgerValidator implements LedgerValidator {
	private Map<FCMap, ResponseCodeEnum> txnHandlerAssessments = new HashMap<>();
	private final EnumSet<ResponseCodeEnum> INVALID_CODES = EnumSet.of(INVALID_ACCOUNT_ID, TOTAL_LEDGER_BALANCE_INVALID);

	@Override
	public void assertIdsAreValid(FCMap<MapKey, HederaAccount> accounts) {
		ResponseCodeEnum assessment =
				txnHandlerAssessments.computeIfAbsent(accounts, TransactionHandler::validateAccountIDAndTotalBalInMap);
		if (assessment == INVALID_ACCOUNT_ID) {
			throw new IllegalStateException("Invalid account id in ledger!");
		}
	}

	@Override
	public boolean hasExpectedTotalBalance(FCMap<MapKey, HederaAccount> accounts) {
		ResponseCodeEnum assessment =
				txnHandlerAssessments.computeIfAbsent(accounts, TransactionHandler::validateAccountIDAndTotalBalInMap);
		return !INVALID_CODES.contains(assessment);
	}
}
