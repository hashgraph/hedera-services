package com.hedera.services.txns.network;

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

import com.google.protobuf.BoolValue;
import com.google.protobuf.UInt64Value;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.MissingAccountException;
import com.hedera.services.ledger.accounts.AccountCustomizer;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.UncheckedSubmitBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.*;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

@RunWith(JUnitPlatform.class)
class UncheckedSubmitTransitionLogicTest {
	UncheckedSubmitTransitionLogic subject = new UncheckedSubmitTransitionLogic();

	@Test
	public void hasExpectedApplicability() {
		// setup:
		var applicability = subject.applicability();

		// expect:
		assertTrue(applicability.test(
				TransactionBody.newBuilder()
						.setUncheckedSubmit(UncheckedSubmitBody.getDefaultInstance()).build()));
		assertFalse(applicability.test(
				TransactionBody.newBuilder()
						.setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance()).build()));
	}

	@Test
	public void rubberstampsEverything() {
		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void throwsIseOnTransitionAttempt() {
		// expect:
		assertDoesNotThrow(subject::doStateTransition);
	}
}