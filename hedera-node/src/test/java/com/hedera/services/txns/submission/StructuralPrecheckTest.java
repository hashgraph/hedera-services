package com.hedera.services.txns.submission;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.*;

class StructuralPrecheckTest {
	private int pretendSizeLimit = 1_000, pretendMaxMessageDepth = 5;
	private StructuralPrecheck subject;

	@BeforeEach
	void setUp() {
		subject = new StructuralPrecheck(pretendSizeLimit, pretendMaxMessageDepth);
	}

	@Test
	void mustHaveBodyBytes() {
		// given:
		var assess = subject.validate(Transaction.getDefaultInstance());

		// then:
		assertExpectedFail(INVALID_TRANSACTION_BODY, assess);
	}

	@Test
	void cantMixSignedBytesWithBodyBytes() {
		// given:
		var assess = subject.validate(Transaction.newBuilder()
				.setSignedTransactionBytes(ByteString.copyFromUtf8("w/e"))
				.setBodyBytes(ByteString.copyFromUtf8("doesn't matter"))
				.build());

		// then:
		assertExpectedFail(INVALID_TRANSACTION, assess);
	}

	@Test
	void cantMixSignedBytesWithSigMap() {
		// given:
		var assess = subject.validate(Transaction.newBuilder()
				.setSignedTransactionBytes(ByteString.copyFromUtf8("w/e"))
				.setSigMap(SignatureMap.getDefaultInstance())
				.build());

		// then:
		assertExpectedFail(INVALID_TRANSACTION, assess);
	}

	@Test
	void cantBeOversize() {
		// given:
		var assess = subject.validate(Transaction.newBuilder()
				.setSignedTransactionBytes(ByteString.copyFromUtf8(IntStream.range(0, pretendSizeLimit)
						.mapToObj(i -> "A")
						.collect(joining())))
				.build());

		// expect:
		assertExpectedFail(TRANSACTION_OVERSIZE, assess);
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
		var assess = subject.validate(signedTxn);

		// then:
		assertExpectedFail(TRANSACTION_TOO_MANY_LAYERS, assess);
	}

	@Test
	void computesExpectedDepth() {
		// setup:
		var weirdlyNestedKey = TxnUtils.nestKeys(Key.newBuilder(), pretendMaxMessageDepth).build();

		// given:
		var expected = legacyCalc(weirdlyNestedKey);

		// when:
		var actualDepth = subject.protoDepthOf(weirdlyNestedKey);

		// then:
		assertEquals(legacyCalc(weirdlyNestedKey), actualDepth);
	}

	private int legacyCalc(GeneratedMessageV3 msg) {
		Map<Descriptors.FieldDescriptor, Object> fields = msg.getAllFields();
		int depth = 0;
		for (var field : fields.values()) {
			if (field instanceof GeneratedMessageV3) {
				GeneratedMessageV3 fieldMessage = (GeneratedMessageV3) field;
				depth = Math.max(depth, legacyCalc(fieldMessage) + 1);
			} else if (field instanceof List) {
				for (Object ele : (List) field) {
					if (ele instanceof GeneratedMessageV3) {
						depth = Math.max(depth, legacyCalc((GeneratedMessageV3) ele) + 1);
					}
				}
			}
		}
		return depth;
	}

	private void assertExpectedFail(ResponseCodeEnum error, Pair<ResponseCodeEnum, Optional<SignedTxnAccessor>> resp) {
		assertEquals(error, resp.getLeft());
		assertTrue(resp.getRight().isEmpty());
	}
}