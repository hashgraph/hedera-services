package com.hedera.services.statecreation.creationtxns;

import com.hedera.services.context.ServicesContext;
import com.hedera.services.statecreation.SavedStateHandler;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;


public class PostCreateTask implements Runnable {
	private static Logger log = LogManager.getLogger(PostCreateTask.class);

	private final static int HALF_MINUE = 30000;
	private final static int TWO_MINUTES = 120000;

	final ServicesContext ctx;
	private final AtomicBoolean allCreated;
	private final Properties properties;
	public PostCreateTask(final AtomicBoolean allCreated,
			final ServicesContext ctx, final Properties properties) {
		this.allCreated = allCreated;
		this.properties = properties;
		this.ctx = ctx;
	}

	@Override
	public void run() {
		while (!allCreated.get()) {
			try {
				log.info("Wait for builtin client to finish...");
				Thread.sleep(HALF_MINUE);
			} catch (InterruptedException e) {

			}
		}

		log.info("Done create the state file and shut down the server.");

		try {
			Transaction txn = FreezeTxnFactory.newFreezeTxn().
					freezeStartAt(Instant.now().plusSeconds(10))
					.get();
			TransactionResponse resp = ctx.submissionFlow().submit(txn);
			ResponseCodeEnum retCode = resp.getNodeTransactionPrecheckCode();
			if(retCode != OK) {
				log.info("Response code is {} for Freeze txn response code is, txn body {}."
						, retCode, txn.toString());
			} else {
				log.info("Successfully submitted Freeze txn ");
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}

		log.info("Sent the freeze command to server and wait its final state file export to finish...");

		int finalWaitTime = 0;
		try {
			finalWaitTime = Integer.parseInt(properties.getProperty("millseconds.waiting.server.down"));
		} catch (NumberFormatException nfe) {
			finalWaitTime = 0;
		}
		if(finalWaitTime <= 0) {
			finalWaitTime = TWO_MINUTES;
		}


		try {
			Thread.sleep(finalWaitTime);
		} catch (InterruptedException e) { }

		SavedStateHandler.zipState();

		String bucketName = properties.getProperty("cloud.bucketname");
		if(bucketName.isEmpty()) {
			bucketName = "services-regression-jrs-files";
		}
		String targetDir = properties.getProperty("cloud.dirForStateFile");
		if(targetDir.isEmpty()) {
			targetDir = "auto-upload-test-dir";
		}
		SavedStateHandler.uploadStateFileGsutil(bucketName, targetDir);

	}
}