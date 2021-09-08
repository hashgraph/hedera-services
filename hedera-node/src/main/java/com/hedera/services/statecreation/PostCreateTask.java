package com.hedera.services.statecreation;

import com.hedera.services.statecreation.creationtxns.FreezeTxnFactory;
import com.hedera.services.txns.submission.BasicSubmissionFlow;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;


public class PostCreateTask implements Runnable {
	private static Logger log = LogManager.getLogger(PostCreateTask.class);

	private static final String DEFAULT_BUCKET_NAME = "services-regression-jrs-files";
	private static final String DEFAULT_BUCKET_DIR = "auto-upload-test-dir";

	private static final int HALF_MINUTE = 30000;
	private static final int TWO_MINUTES = 120000;

	final BasicSubmissionFlow submissionFlow;
	private final AtomicBoolean allCreated;
	private final Properties properties;
	public PostCreateTask(final AtomicBoolean allCreated,
			final BasicSubmissionFlow submissionFlow,
			final Properties properties) {
		this.allCreated = allCreated;
		this.properties = properties;
		this.submissionFlow = submissionFlow;
	}

	@Override
	public void run() {
		while (!allCreated.get()) {
			try {
				log.info("Wait for builtin client to finish...");
				Thread.sleep(HALF_MINUTE);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		log.info("Done create the state file and shut down the server.");

		try {
			Transaction txn = FreezeTxnFactory.newFreezeTxn().
					freezeStartAt(Instant.now().plusSeconds(10))
					.get();
			TransactionResponse resp = submissionFlow.submit(txn);
			ResponseCodeEnum retCode = resp.getNodeTransactionPrecheckCode();
			if(retCode != OK) {
				log.info("Response code is {} for Freeze txn, txn body {}.", retCode, txn);
			} else {
				log.info("Successfully submitted Freeze txn.");
			}
		} catch (IllegalStateException e) {
			log.warn("Freeze command was not finished successfully ", e);
		}

		log.info("Sent the freeze command to server and wait its final state file export to finish...");

		int finalWaitTime = 0;
		String prop = "millseconds.waiting.server.down";
		try {
			finalWaitTime = Integer.parseInt(properties.getProperty(prop));
		} catch (NumberFormatException nfe) {
			log.warn("Property {} is not an integer. Using 0 as default vale.", prop);
		}
		if(finalWaitTime <= 0) {
			finalWaitTime = TWO_MINUTES;
		}

		try {
			Thread.sleep(finalWaitTime);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		SavedStateHandler.zipState();

		String bucketName = properties.getProperty("cloud.bucketname");
		if(bucketName.isEmpty()) {
			bucketName = DEFAULT_BUCKET_NAME;
		}
		String targetDir = properties.getProperty("cloud.dirForStateFile");
		if(targetDir.isEmpty()) {
			targetDir = DEFAULT_BUCKET_DIR;
		}
		SavedStateHandler.uploadStateFileGsutil(bucketName, targetDir, properties);
	}
}