package com.hedera.services.legacy.unit.handler;

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

import com.hedera.services.config.MockAccountNumbers;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.StandardExemptions;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.context.ContextPlatformStatus;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.records.RecordCache;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.TransactionThrottling;
import com.hedera.services.txns.validation.BasicPrecheck;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.Platform;
import com.swirlds.common.PlatformStatus;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
public class TransactionHandlerTest {
	private Platform platform;
	private Transaction request;
	private TransactionID txnId;

	private RecordCache recordCache;
	private PrecheckVerifier precheckVerifier;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	private AccountID nodeAccount;
	private TransactionThrottling txnThrottling;
	private UsagePricesProvider usagePrices;
	private HbarCentExchange exchange;
	private FeeCalculator fees;
	private Supplier<StateView> stateView;
	private BasicPrecheck basicPrecheck;
	private QueryFeeCheck queryFeeCheck;
	private FunctionalityThrottling throttling;

	private TransactionHandler subject;

	@BeforeEach
	public void setUp() {
		platform = mock(Platform.class);
		request = mock(Transaction.class);
		txnId = mock(TransactionID.class);

		recordCache = mock(RecordCache.class);
		precheckVerifier = mock(PrecheckVerifier.class);
		accounts = mock(FCMap.class);
		nodeAccount = mock(AccountID.class);
		txnThrottling = mock(TransactionThrottling.class);
		usagePrices = mock(UsagePricesProvider.class);
		exchange = mock(HbarCentExchange.class);
		fees = mock(FeeCalculator.class);
		stateView = mock(Supplier.class);
		basicPrecheck = mock(BasicPrecheck.class);
		queryFeeCheck = mock(QueryFeeCheck.class);
		throttling = mock(FunctionalityThrottling.class);

		var policies = new SystemOpPolicies(new MockEntityNumbers());
		var platformStatus = new ContextPlatformStatus();
		platformStatus.set(PlatformStatus.ACTIVE);
		subject = new TransactionHandler(
				recordCache,
				precheckVerifier,
				() -> accounts,
				nodeAccount,
				txnThrottling,
				fees,
				stateView,
				basicPrecheck,
				queryFeeCheck,
				throttling,
				new MockAccountNumbers(),
				policies,
				new StandardExemptions(new MockAccountNumbers(), policies),
				platformStatus);
	}
}
