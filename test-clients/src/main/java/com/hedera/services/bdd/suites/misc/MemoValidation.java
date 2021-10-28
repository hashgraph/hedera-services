package com.hedera.services.bdd.suites.misc;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;

public class MemoValidation extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(MemoValidation.class);

	public static void main(String... args) {
		new MemoValidation().runSuiteAsync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				memoValidations()
		);
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}

	private HapiApiSpec memoValidations() {
		char singleByteChar = 'a';
		char multiByteChar = 'ф';

		byte[] longBytes = new byte[1000];
		byte[] validBytes = new byte[100];
		byte[] inValidBytes = new byte[101];
		byte[] bytes_49 = new byte[49];
		Arrays.fill(longBytes, (byte) 33);
		Arrays.fill(validBytes, (byte)multiByteChar);
		Arrays.fill(inValidBytes, (byte)multiByteChar);
		Arrays.fill(bytes_49, (byte)singleByteChar);

		String longMemo = new String(longBytes, StandardCharsets.UTF_8);
		String validMemoWithMultiByteChars = new String(validBytes, StandardCharsets.UTF_8);
		String inValidMemoWithMultiByteChars = new String(inValidBytes, StandardCharsets.UTF_8);
		String stringOf49Bytes = new String(bytes_49, StandardCharsets.UTF_8);

		return defaultHapiSpec("MemoValidations")
				.given().when().then(
						createTopic("testTopic")
								.topicMemo(longMemo)
								.hasKnownStatus(MEMO_TOO_LONG),
						createTopic("alsoTestTopic")
								.topicMemo(ZERO_BYTE_MEMO)
								.hasKnownStatus(INVALID_ZERO_BYTE_IN_STRING),
						createTopic("validMemoWithMultiByteChars")
								.topicMemo(validMemoWithMultiByteChars),
						createTopic("inValidMemoWithMultiByteChars")
								.topicMemo(inValidMemoWithMultiByteChars)
								.hasKnownStatus(MEMO_TOO_LONG),
						createTopic("validMemo1")
								.topicMemo(stringOf49Bytes + multiByteChar + stringOf49Bytes),
						createTopic("validMemo2")
								.topicMemo(stringOf49Bytes + singleByteChar + singleByteChar + stringOf49Bytes),
						scheduleCreate("invalidMemo", cryptoCreate("test"))
								.withEntityMemo(stringOf49Bytes + singleByteChar + multiByteChar + stringOf49Bytes)
								.hasKnownStatus(MEMO_TOO_LONG)
				);
	}
}
