package com.hedera.services.statecreation;

import com.hedera.services.context.ServicesContext;
import com.hedera.services.statecreation.creationtxns.CryptoCreateTxnFactory;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.swirlds.common.TransactionType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class BuiltinClient implements Runnable {
	static Logger log = LogManager.getLogger(BuiltinClient.class);

	final private AtomicInteger totalToCreate;
	final private String entityType;
	ServicesContext ctx;


	public BuiltinClient(final AtomicInteger totalToCreate, final String entityType, ServicesContext ctx) {
		this.totalToCreate = totalToCreate;
		this.entityType = entityType;
		this.ctx = ctx;
	}

	@Override
	public void run() {
		while(true) {
			try {
				int i = totalToCreate.decrementAndGet();
				if (i > 0) {
					Transaction txn = CryptoCreateTxnFactory.newSignedCryptoCreate()
							.balance(i * 1_000_000_000L)
							//.sigMapGen()
							//.skipPayerSig()
							.receiverSigRequired(false)
							.fee(1_000_000_000L)
							.get();
					TransactionResponse resp = ctx.submissionFlow().submit(txn);
					ResponseCodeEnum retCode = resp.getNodeTransactionPrecheckCode();
					if(retCode != OK) {
						log.info("Response code is {} for CryptoCreate txn {} response code is: ",retCode , txn.toString());
					}
				}
				else {
					break;
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

	}
}
