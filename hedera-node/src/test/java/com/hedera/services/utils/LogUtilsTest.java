package com.hedera.services.utils;

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogUtilsTest {
	private static final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
	private static final PrintStream originalOut = System.out;

	@BeforeAll
	public static void setUpStreams() {
		System.setOut(new PrintStream(outContent));
	}

	@AfterAll
	public static void restoreStreams() {
		System.setOut(originalOut);
	}

	@Test
	void ignoresJndiInGrpc() {
		final var malUri = "${jndi:https://previewnet.mirrornode.hedera.com/api/v1/accounts?account.id=0.0.90}";
		final var message = "We re Doomed!! %s";
		final var expectedLog = String.format(message, malUri);
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
		LogUtils.encodeGrpcAndLog(logger, Level.WARN, message, txnBody);

		final var actualLogWithNoJndiLookUp = outContent.toString();
		assertFalse(actualLogWithNoJndiLookUp.contains(stackStraceSample));
		assertTrue(actualLogWithNoJndiLookUp.contains(expectedLog));

		// If using log4J version 2.15 or older the following testing is relevant.
//		logger.log(Level.WARN, malUri);
//
//		final var actualLogWithJndiLookUp = outContent.toString();
//		assertTrue(actualLogWithJndiLookUp.contains(stackStraceSample));
	}
	@Test
	void test() {
		final String logMessage = String.format("Possibly CATASTROPHIC failure in %s :: %s ==>> %s ==>>", "123", "%s", "456");
		System.out.println(logMessage);
	}
}
