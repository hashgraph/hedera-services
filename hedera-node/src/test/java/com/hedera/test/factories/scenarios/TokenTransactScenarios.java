package com.hedera.test.factories.scenarios;

import com.hedera.services.utils.PlatformTxnAccessor;

import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.TokenTransactFactory.newSignedTokenTransact;

public enum TokenTransactScenarios implements TxnHandlingScenario {
	TOKEN_TRANSACT_WITH_EXTANT_SENDERS {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenTransact()
							.adjusting(FIRST_TOKEN_SENDER, KNOWN_TOKEN_NO_FREEZE, -1_000)
							.adjusting(SECOND_TOKEN_SENDER, KNOWN_TOKEN_NO_FREEZE, -1_000)
							.adjusting(TOKEN_RECEIVER, KNOWN_TOKEN_NO_FREEZE, +2_000)
							.nonPayerKts(FIRST_TOKEN_SENDER_KT, SECOND_TOKEN_SENDER_KT)
							.get()
			));
		}
	},
	TOKEN_TRANSACT_WITH_MISSING_SENDERS {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenTransact()
							.adjusting(FIRST_TOKEN_SENDER, KNOWN_TOKEN_NO_FREEZE, -1_000)
							.adjusting(MISSING_ACCOUNT, KNOWN_TOKEN_NO_FREEZE, -1_000)
							.adjusting(TOKEN_RECEIVER, KNOWN_TOKEN_NO_FREEZE, +2_000)
							.nonPayerKts(FIRST_TOKEN_SENDER_KT, SECOND_TOKEN_SENDER_KT)
							.get()
			));
		}
	},
}
