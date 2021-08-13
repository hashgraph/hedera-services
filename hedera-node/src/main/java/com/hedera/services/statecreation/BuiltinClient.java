package com.hedera.services.statecreation;

import com.hedera.services.context.ServicesContext;
import com.hedera.services.statecreation.creationtxns.ContractCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.CryptoCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.TokenAssociateCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.TokenCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.TopicCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.FileCreateTxnFactory;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hedera.services.statecreation.creationtxns.utils.TempUtils.asAccount;


public class BuiltinClient implements Runnable {
	static Logger log = LogManager.getLogger(BuiltinClient.class);

	final private static int SYSTEM_ACCOUNTS = 1000;
	final private Map<Integer, String> processOrders;
	final private Properties properties;
	final private AtomicBoolean allCreated;
	final ServicesContext ctx;
	private Random random = new Random();

	private static int totalAccounts;
	private static int tokenNumStart;
	private static int totalTokens;

	private static AtomicInteger currentEntityNumEnd = new AtomicInteger(1001);

	public BuiltinClient(final Properties layoutProps,
			final Map<Integer, String> processOrders, final ServicesContext ctx,
			final AtomicBoolean allCreated) {
		this.processOrders = processOrders;
		this.properties = layoutProps;
		this.ctx = ctx;
		this.allCreated = allCreated;
	}

	@Override
	public void run() {
		// TODO:  Maybe a latch or phaser to wait for services to start up, otherwise we may get platform not active
		log.info("In built client, wait for server to start up...");
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {

		}

		processOrders.forEach(this::createEntitiesFor);

		log.info("All entities created. Shutdown the client thread");
		allCreated.set(true);
	}

	private void createEntitiesFor(Integer posi, String entityType) {
		final String valStr = properties.getProperty(processOrders.get(posi) + ".total");
		int totalToCreate = Integer.parseInt(valStr);

		if(totalToCreate > 0) {
			log.info("Start to build " + valStr + " " + entityType + "Starting number: " + currentEntityNumEnd.get());
			createEntitiesFor(entityType,  totalToCreate, properties, processOrders);
			log.info( entityType + " value range [{} - {}]",
					currentEntityNumEnd.get(),currentEntityNumEnd.get() + totalToCreate);

			currentEntityNumEnd.set(currentEntityNumEnd.get() + totalToCreate);
		}
	}

	private void createEntitiesFor(final String entityType,
			final int totalToCreate,
			final Properties properties,
			final Map<Integer, String> layoutProps) {

		switch (entityType) {
			case "accounts":
				createAccounts(totalToCreate);
				break;
			case "topics":
				createTopics(totalToCreate, properties);
				break;
			case "tokens":
				createTokens(totalToCreate, properties);
				break;

			case "files":
				createFiles(totalToCreate, properties);
				break;
			case "smartContracts":
				createSmartContracts(totalToCreate, properties);
				break;
			case "tokenAssociations":
				createTokenAssociations(totalToCreate, properties);
				break;

			default:
				log.info("not implemented yet for " + entityType);
				break;
		}
	}

	private void createAccounts(final int totalToCreate) {
		int i = 0;
		while(i < totalToCreate) {
			try {
				Transaction txn = CryptoCreateTxnFactory.newSignedCryptoCreate()
						.balance(i * 1_000_000L)
						.receiverSigRequired(false)
						.fee(1_000_000_000L)
						.memo("Memo for account " + i)
						.get();
				TransactionResponse resp = ctx.submissionFlow().submit(txn);
				handleTxnResponse(resp, i, "AccountCreate", txn, totalToCreate / 10);
			} catch (Throwable e) {
				e.printStackTrace();
			}
			i++;
		}

		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			log.warn("Post account creation sleep gets interrupted.");
		}

		// TODO: export the last created account number, this should be the ending boundary of all accounts
		totalAccounts = totalToCreate;
		log.info("Done creating {} accounts", totalToCreate);
	}

	private void createTopics(final int totalToCreate, final Properties properties) {
		int i = 0;
		while(i < totalToCreate) {
			try {
				Transaction txn = TopicCreateTxnFactory.newSignedConsensusCreateTopic()
						.fee(1_000_000_000L)
						.payer("0.0." + selectRandomAccount())
						.memo("Memo for topics " + i)  // Need random memo
						.get();
				TransactionResponse resp = ctx.submissionFlow().submit(txn);
				handleTxnResponse(resp, i, "TopicCreate", txn, totalToCreate/10);
			} catch (Throwable e) {
				e.printStackTrace();
			}
			i++;
		}
		// TODO: same here to export the last entity created for later and EET usage
		log.info("Done creating {} topics", totalToCreate);
	}

	private void createTokens(final int totalToCreate, final Properties properties ) {
		String totalAcctStr = properties.getProperty("accounts.total");
		int i = 0;
		while(i < totalToCreate) {
			try {
				Transaction txn = TokenCreateTxnFactory.newSignedTokenCreate()
						.fee(1_000_000_000L)
						.name("token" + i)
						.symbol("SYMBOL" + i)
						.treasury(asAccount("0.0." + selectRandomAccount()))
						.get();
				TransactionResponse resp = ctx.submissionFlow().submit(txn);
				handleTxnResponse(resp, i, "TokenCreate", txn, totalToCreate/10);
			} catch (Throwable e) {
				e.printStackTrace();
			}
			i++;
		}
		tokenNumStart = currentEntityNumEnd.get();
		totalTokens = totalToCreate;
		log.info("Done creating {} tokens", totalToCreate);
	}

	private void createFiles(final int totalToCreate, final Properties properties ) {
		int i = 0;
		while(i < totalToCreate) {
			try {
				Transaction txn = FileCreateTxnFactory.newSignedFileCreate()
						.fee(1_000_000_000L)
						.get();
				TransactionResponse resp = ctx.submissionFlow().submit(txn);
				handleTxnResponse(resp, i, "FileCreate", txn, totalToCreate/10);
			} catch (Throwable e) {
				e.printStackTrace();
			}
			i++;
		}
		log.info("Done creating {} files", totalToCreate);
	}

	private void createSmartContracts(final int totalToCreate, final Properties properties ) {
		int i = 0;
		while(i < totalToCreate) {
			try {
				Transaction txn = ContractCreateTxnFactory.newSignedContractCreate()
						.fee(1_000_000_000L)
						.get();
				TransactionResponse resp = ctx.submissionFlow().submit(txn);
				handleTxnResponse(resp, i, "SmartContractCreate", txn, totalToCreate/10);
			} catch (Throwable e) {
				e.printStackTrace();
			}
			i++;
		}
		log.info("Done creating {} smart contracts", totalToCreate);
	}

	private void createTokenAssociations(final int totalToCreate, final Properties properties ) {
		int i = 0;

		while(i < totalToCreate) {
			try {
				Transaction txn = TokenAssociateCreateTxnFactory.newSignedTokenAssociate()
						.fee(1_000_000_000L)
						.targeting(selectRandomAccount())
						.associating(selectRandomToken())
						.get();
				TransactionResponse resp = ctx.submissionFlow().submit(txn);
				handleTxnResponse(resp, i, "TokenAssociationCreate", txn, totalToCreate/10);
			} catch (Throwable e) {
				e.printStackTrace();
			}
			i++;
		}
		log.info("Done creating {} Token Associations", totalToCreate);
	}


	private void handleTxnResponse(final TransactionResponse resp,
			final int index,
			final String txnType,
			final Transaction txn,
			final int pageSize ) {
		ResponseCodeEnum retCode = resp.getNodeTransactionPrecheckCode();
		if(retCode != OK) {
			log.info("Response code is {} for {} txn #{} and body {}",
					retCode , txnType, index, txn.toString());
		} else {
			if(index % pageSize == 0) {
				log.info("Successfully submitted {} txn #{} ", txnType, index);
			}
		}
	}

	private int selectRandomAccount() {
		return SYSTEM_ACCOUNTS + random.nextInt(totalAccounts);
	}
	private int selectRandomToken() {
		return tokenNumStart + random.nextInt(totalTokens);
	}
}
