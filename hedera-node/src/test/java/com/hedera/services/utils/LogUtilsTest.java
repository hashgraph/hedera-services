package com.hedera.services.utils;

import com.google.protobuf.ByteString;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogUtilsTest {
//	private static final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
//	private static final PrintStream originalOut = System.out;
	private static final String HGCAA_LOG_PATH = "output/hgcaa.log";

	@BeforeAll
	public static void clearHgcaaLog() throws FileNotFoundException {
		new PrintWriter(HGCAA_LOG_PATH).close();
//		System.setOut(new PrintStream(outContent));
	}

//	@AfterAll
//	public static void restoreStreams() {
//		System.setOut(originalOut);
//	}

	@Test
	void ignoresJndiInGrpc() throws IOException {
		final var malUri = "${jndi:https://previewnet.mirrornode.hedera.com/api/v1/accounts?account.id=0.0.90}";
		final var escapedUri = LogUtils.escapeBytes(ByteString.copyFromUtf8(malUri));
		final var message = "We are Doomed!! %s";
		final var stackStraceSample = "at org.apache.logging.log4j.core.net.JndiManager.lookup";
		final CryptoCreateTransactionBody createTxnBody = CryptoCreateTransactionBody.newBuilder()
				.setMemo(malUri)
				.setInitialBalance(0L)
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(THREE_MONTHS_IN_SECONDS))
				.build();

		final var txnBody = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(IdUtils.asAccount("0.0.2")))
				.setCryptoCreateAccount(createTxnBody).build();
		final var txn = Transaction.newBuilder().setSignedTransactionBytes(txnBody.toByteString()).build();

		final var logger = LogManager.getLogger();

		// log4j-test has no {nolookups} tag, so if the encoding is not done for grpc, we can exploit log4J
		LogUtils.encodeGrpcAndLog(logger, Level.WARN, message, txn);

		final var actualLogWithNoJndiLookUp = readHgcaaLog();
		assertFalse(actualLogWithNoJndiLookUp.contains(stackStraceSample));
		assertTrue(actualLogWithNoJndiLookUp.contains(escapedUri),
				"actual : " + actualLogWithNoJndiLookUp + " expected : " + escapedUri);

		// If using log4J version 2.14 or older the following test code is relevant.
		/*
		logger.log(Level.WARN, malUri);

		final var actualLogWithJndiLookUp = outContent.toString();
		assertTrue(actualLogWithJndiLookUp.contains(stackStraceSample));
		 */
	}

	@Test
	void unescapesCorrectly() throws LogUtils.InvalidEscapeSequenceException {
		final var malUri = "${jndi:https://previewnet.mirrornode.hedera.com/api/v1/accounts?account.id=0.0.90}";

		assertEquals(malUri, LogUtils.unescapeBytes(LogUtils.escapeBytes(ByteString.copyFromUtf8(malUri))));
	}

	private String readHgcaaLog() throws IOException {
		return Files.readString(Path.of(HGCAA_LOG_PATH), StandardCharsets.UTF_8);
	}
}
