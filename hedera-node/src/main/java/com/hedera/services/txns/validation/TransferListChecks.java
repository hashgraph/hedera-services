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

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountAmountOrBuilder;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.api.proto.java.TransferListOrBuilder;

import java.math.BigInteger;
import java.util.HashSet;

import static java.math.BigInteger.ZERO;
import static java.util.stream.Collectors.toSet;

/**
 * Offers a few static helpers to evaluate {@link TransferList} instances
 * presented by incoming gRPC transactions.
 *
 * @author Michael Tinker
 */
public class TransferListChecks {
	public static boolean isNetZeroAdjustment(TransferListOrBuilder wrapper) {
		var net = ZERO;
		for (AccountAmountOrBuilder adjustment : wrapper.getAccountAmountsOrBuilderList()) {
			net = net.add(BigInteger.valueOf(adjustment.getAmount()));
		}
		return net.equals(ZERO);
	}

	public static boolean hasRepeatedAccount(TransferList wrapper) {
		var unique = new HashSet<AccountID>();
		for (AccountAmount adjustment : wrapper.getAccountAmountsList()) {
			unique.add(adjustment.getAccountID());
		}
		return unique.size() < wrapper.getAccountAmountsCount();
	}
}
