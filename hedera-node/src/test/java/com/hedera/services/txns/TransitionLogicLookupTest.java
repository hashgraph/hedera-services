package com.hedera.services.txns;

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

import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TransitionLogicLookupTest {
	TransitionLogic a = withApplicability(txn -> txn.getTransactionID().getAccountID().equals(asAccount("0.0.2")));
	TransitionLogic b = withApplicability(txn -> txn.getTransactionID().getAccountID().equals(asAccount("2.2.0")));
	Map<HederaFunctionality, List<TransitionLogic>> transitionsMap = Map.ofEntries(
			Map.entry(CryptoTransfer, List.of(b, a))
	);
	TransitionLogicLookup subject = new TransitionLogicLookup(transitionsMap::get);
	TransactionBody aTxn = TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder().setAccountID(asAccount("0.0.2")))
			.setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
			.build();
	TransactionBody zTxn = TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder().setAccountID(asAccount("9.0.2")))
			.setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance())
			.build();

	@Test
	public void identifiesMissing() {
		// expect:
		assertFalse(subject.lookupFor(CryptoCreate, zTxn).isPresent());
		assertFalse(subject.lookupFor(CryptoTransfer, zTxn).isPresent());
	}

	@Test
	public void identifiesLogic() {
		// expect:
		assertEquals(a, subject.lookupFor(CryptoTransfer, aTxn).get());
	}

	private TransitionLogic withApplicability(Predicate<TransactionBody> p) {
		return new TransitionLogic() {
			@Override
			public void doStateTransition() {
			}

			@Override
			public Predicate<TransactionBody> applicability() {
				return p;
			}

			@Override
			public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
				return ignore -> SUCCESS;
			}
		};
	}
}
