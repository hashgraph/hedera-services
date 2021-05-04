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

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.*;

class StructuralPrecheckTest {
	private int pretendSizeLimit = 1_000, pretendMaxMessageDepth = 42;
	private StructuralPrecheck subject;

	@BeforeEach
	void setUp() {
		subject = new StructuralPrecheck(pretendSizeLimit, pretendMaxMessageDepth);
	}

	@Test
	void mustHaveBodyBytes() {
		// given:
		var assess = subject.assess(Transaction.getDefaultInstance());

		// then:
		assertExpectedFail(INVALID_TRANSACTION_BODY, assess);
	}

	@Test
	void cantMixSignedBytesWithBodyBytes() {
		// given:
		var assess = subject.assess(Transaction.newBuilder()
				.setSignedTransactionBytes(ByteString.copyFromUtf8("w/e"))
				.setBodyBytes(ByteString.copyFromUtf8("doesn't matter"))
				.build());

		// then:
		assertExpectedFail(INVALID_TRANSACTION, assess);
	}

	@Test
	void cantMixSignedBytesWithSigMap() {
		// given:
		var assess = subject.assess(Transaction.newBuilder()
				.setSignedTransactionBytes(ByteString.copyFromUtf8("w/e"))
				.setSigMap(SignatureMap.getDefaultInstance())
				.build());

		// then:
		assertExpectedFail(INVALID_TRANSACTION, assess);
	}

	@Test
	void cantBeOversize() {
		// given:
		var assess = subject.assess(Transaction.newBuilder()
				.setSignedTransactionBytes(ByteString.copyFromUtf8(IntStream.range(0, pretendSizeLimit)
						.mapToObj(i -> "A")
						.collect(joining())))
				.build());

		// expect:
		assertExpectedFail(TRANSACTION_OVERSIZE, assess);
	}

	@Test
	void mustParseViaAccessor() {
		// given:
		var assess = subject.assess(Transaction.newBuilder()
				.setSignedTransactionBytes(ByteString.copyFromUtf8("NONSENSE"))
				.build());

		// expect:
		assertExpectedFail(INVALID_TRANSACTION_BODY, assess);
	}

	@Test
	void cantBeUndulyNested() {
		// given:
		var weirdlyNestedKey = TxnUtils.nestKeys(Key.newBuilder(), pretendMaxMessageDepth);
		var hostTxn = TransactionBody.newBuilder()
				.setCryptoCreateAccount(CryptoCreateTransactionBody.newBuilder()
						.setKey(weirdlyNestedKey));
		var signedTxn = Transaction.newBuilder().setBodyBytes(hostTxn.build().toByteString()).build();

		// when:
		var assess = subject.assess(signedTxn);

		// then:
		assertExpectedFail(TRANSACTION_TOO_MANY_LAYERS, assess);
	}

	@Test
	void cantOmitAFunction() {
		// given:
		var hostTxn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(IdUtils.asAccount("0.0.2")));
		var signedTxn = Transaction.newBuilder().setBodyBytes(hostTxn.build().toByteString()).build();

		// when:
		var assess = subject.assess(signedTxn);

		// then:
		assertExpectedFail(INVALID_TRANSACTION_BODY, assess);
	}

	@Test
	void canBeOk() {
		// given:
		var reasonablyNestedKey = TxnUtils.nestKeys(Key.newBuilder(), 2);
		var hostTxn = TransactionBody.newBuilder()
				.setCryptoCreateAccount(CryptoCreateTransactionBody.newBuilder()
						.setKey(reasonablyNestedKey));
		var signedTxn = Transaction.newBuilder().setBodyBytes(hostTxn.build().toByteString()).build();

		// when:
		var assess = subject.assess(signedTxn);

		// then:
		assertEquals(OK, assess.getLeft().getValidity());
		assertTrue(assess.getRight().isPresent());
		assertEquals(HederaFunctionality.CryptoCreate, assess.getRight().get().getFunction());
	}

	@Test
	void computesExpectedDepth() {
		// setup:
		var weirdlyNestedKey = TxnUtils.nestKeys(Key.newBuilder(), pretendMaxMessageDepth).build();

		// given:
		var expectedDepth = verboseCalc(weirdlyNestedKey);

		// when:
		var actualDepth = subject.protoDepthOf(weirdlyNestedKey);

		// then:
		assertEquals(expectedDepth, actualDepth);
	}

	private int verboseCalc(GeneratedMessageV3 msg) {
		Map<Descriptors.FieldDescriptor, Object> fields = msg.getAllFields();
		int depth = 0;
		for (var field : fields.values()) {
			if (field instanceof GeneratedMessageV3) {
				GeneratedMessageV3 fieldMessage = (GeneratedMessageV3) field;
				depth = Math.max(depth, verboseCalc(fieldMessage) + 1);
			} else if (field instanceof List) {
				for (Object ele : (List) field) {
					if (ele instanceof GeneratedMessageV3) {
						depth = Math.max(depth, verboseCalc((GeneratedMessageV3) ele) + 1);
					}
				}
			}
		}
		return depth;
	}

	private void assertExpectedFail(
			ResponseCodeEnum error,
			Pair<TxnValidityAndFeeReq, Optional<SignedTxnAccessor>> resp
	) {
		assertEquals(error, resp.getLeft().getValidity());
		assertTrue(resp.getRight().isEmpty());
	}
}
