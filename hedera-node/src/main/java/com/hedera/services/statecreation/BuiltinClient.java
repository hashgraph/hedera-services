package com.hedera.services.statecreation;

import com.hedera.services.context.ServicesContext;
import com.hedera.services.statecreation.creationtxns.CryptoCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.TokenCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.TopicCreateTxnFactory;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hedera.services.statecreation.creationtxns.utils.IdUtils.asAccount;


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
		switch (entityType) {
			case "accounts":
				createAccounts();
				break;
			case "topics":
				createTopics();
				break;
			case "tokens":
				createTokens();
				break;

			default:
				System.out.println("not implemented yet for " + entityType);
				break;
		}
	}

	private void createAccounts() {
		int total = totalToCreate.get();
		while(true) {
			try {
				int i = totalToCreate.decrementAndGet() + 1;
				if (i > 0) {
					Transaction txn = CryptoCreateTxnFactory.newSignedCryptoCreate()
							.balance(i * 1_000_000L)
							.receiverSigRequired(false)
							.fee(1_000_000_000L)
							.memo("Memo for account " + i)
							.get();
					TransactionResponse resp = ctx.submissionFlow().submit(txn);
					ResponseCodeEnum retCode = resp.getNodeTransactionPrecheckCode();
					if(retCode != OK) {
						log.info("Response code is {} for CryptoCreate txn #{} and body {} response code is: ",retCode , i, txn.toString());
					} else {
						if(i % 5000 == 0) {
							log.info("Successfully submitted CryptoCreate txn #{} ", i);
						}
					}
				}
				else {
					log.info("Done creating {} accounts", total);
					break;
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	private void createTopics() {
		int total_accounts = 1000; // TODO: get it from entity-layout.properties;
		Random random = new Random();
		int total = totalToCreate.get();
		// TODO: refactoring this
		while(true) {
			try {
				int i = totalToCreate.decrementAndGet() + 1;
				if (i > 0) {
					Transaction txn = TopicCreateTxnFactory.newSignedConsensusCreateTopic()
							//.balance(i * 1_000_000L)
							//.receiverSigRequired(false)
							.fee(1_000_000_000L)
//							.payer("0.0." + random.nextInt(total_accounts))
							.memo("Memo for topics " + i)  // Need random memo
							.get();
					TransactionResponse resp = ctx.submissionFlow().submit(txn);
					ResponseCodeEnum retCode = resp.getNodeTransactionPrecheckCode();
					if(retCode != OK) {
						log.info("Response code is {} for TopicCreate txn #{} and body {} response code is: ",retCode , i, txn.toString());
					} else {
						if(i % 1000 == 0) {
							log.info("Successfully submitted TopicCreate txn #{} ", i);
						}
					}
				}
				else {
					log.info("Done creating {} topics", total);
					break;
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	private void createTokens() {
		int total_accounts = 1000;
		Random random = new Random();
		int total = totalToCreate.get();
		// TODO: refactoring this
		while(true) {
			try {
				int i = totalToCreate.decrementAndGet() + 1;
				if (i > 0) {
					Transaction txn = TokenCreateTxnFactory.newSignedTokenCreate()
							.fee(1_000_000_000L)
							.name("token" + i)
							.symbol("SYMBOL" + i)
							.treasury(asAccount("0.0." + random.nextInt(total_accounts)))
							.get();
					TransactionResponse resp = ctx.submissionFlow().submit(txn);
					ResponseCodeEnum retCode = resp.getNodeTransactionPrecheckCode();
					if(retCode != OK) {
						log.info("Response code is {} for TokenCreate txn #{} and body {} response code is: ",retCode , i, txn.toString());
					} else {
						if(i % 1000 == 0) {
							log.info("Successfully submitted TokenCreate txn #{} ", i);
						}
					}
				}
				else {
					log.info("Done creating {} tokens", total);
					break;
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}
}
