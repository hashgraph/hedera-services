package com.hedera.services.statecreation.creationtxns;

import com.hedera.services.context.ServicesContext;
import com.hedera.services.statecreation.SavedStateHandler;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;


public class PostCreateTask implements Runnable {
	private static Logger log = LogManager.getLogger(PostCreateTask.class);

	final ServicesContext ctx;
	private final AtomicBoolean allCreated;

	public PostCreateTask(final AtomicBoolean allCreated, final ServicesContext ctx) {
		this.allCreated = allCreated;
		this.ctx = ctx;
	}

	@Override
	public void run() {
		while (!allCreated.get()) {
			try {
				log.info("Wait for builtin client to finish...");
				Thread.sleep(1000);
			} catch (InterruptedException e) {

			}
		}

		log.info("Done create the state file and let's shutdown the server.");

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

		log.info("Sent the freeze command to server");

		// wait a little bit or check swirlds.log to find the "MAINTENANCE" flag,
		// then gzip and upload the generated saved files
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {

		}

		SavedStateHandler.zipState();

		// TODO: fix this: Exception in thread "pool-3-thread-2" com.google.cloud.storage.StorageException: No trusted certificate found
		// SavedStateHandler.uploadStateFile();
	}
}