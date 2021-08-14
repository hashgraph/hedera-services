package com.hedera.services.statecreation;

import com.google.common.base.Stopwatch;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.statecreation.creationtxns.ContractCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.CreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.CryptoCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.NftCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.ScheduleCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.TokenAssociateCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.TokenCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.TopicCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.FileCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.UniqTokenCreateTxnFactory;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hedera.services.statecreation.creationtxns.utils.TempUtils.asAccount;


public class BuiltinClient implements Runnable {
	static Logger log = LogManager.getLogger(BuiltinClient.class);

	final private static int MAX_NFT_PER_TOKEN = 1000;
	final private static int NFT_MINT_BATCH_SIZE = 10;

	final private static int SYSTEM_ACCOUNTS = 1000;
	final private Map<Integer, String> processOrders;
	final private Properties properties;
	final private AtomicBoolean allCreated;
	final ServicesContext ctx;
	private Random random = new Random();

	private static int totalAccounts;
	private static int tokenNumStart;
	private static int totalTokens;
	private static int uniqTokenNumStart;
	private static int totalUniqTokens;
	private static int contractFileNumStart;
	private static int totalContractFiles;

	private Stopwatch stopWatch = Stopwatch.createUnstarted();


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
		log.info("In built client, wait for server to start up from genesis...");
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {	}

		stopWatch.start();
		processOrders.forEach(this::createEntitiesFor);

		log.info("All entities created. Shutdown the client thread");

		try {
			Thread.sleep(30000);
		} catch (InterruptedException e) {	}

		allCreated.set(true);
	}

	private void createEntitiesFor(Integer posi, String entityType) {

		String valStr = properties.getProperty(processOrders.get(posi) + ".total");
		int totalToCreate = Integer.parseInt(valStr);
		valStr = properties.getProperty(entityType + ".creation.rate");
		log.info("Create for : " + entityType + ", rate: " + valStr);

		int creationRate = Integer.parseInt(valStr);

		if(totalToCreate > 0) {
			log.info("Start to build " + valStr + " " + entityType + ". Starting number: " + currentEntityNumEnd.get() + ", at rate: " + creationRate);
			createEntitiesFor(entityType,  totalToCreate, creationRate, properties, processOrders);
			log.info( entityType + " value range [{} - {}]",
					currentEntityNumEnd.get(),currentEntityNumEnd.get() + totalToCreate - 1);

			log.info("Current seqNo: " + ctx.seqNo().current());
			currentEntityNumEnd.set(currentEntityNumEnd.get() + totalToCreate);
		}
	}

	private void createEntitiesFor(final String entityType,
			final int totalToCreate,
			final int creationRate,
			final Properties properties,
			final Map<Integer, String> layoutProps) {

		switch (entityType) {
			case "accounts":
				createAccounts(totalToCreate, creationRate);
				break;
			case "topics":
				createTopics(totalToCreate, creationRate, properties);
				break;
			case "tokens":
				createTokens(totalToCreate, creationRate, properties);
				break;
			case "files":
				createFiles(totalToCreate, creationRate, false, properties);
				break;
			case "smartContracts":
				createSmartContracts(totalToCreate, creationRate, properties);
				break;
			case "tokenAssociations":
				createTokenAssociations(totalToCreate, creationRate, properties);
				break;
			case "uniqueTokens":
				createUniqTokens(totalToCreate, creationRate, properties);
				break;
			case "nfts":
				createNfts(totalToCreate, creationRate, properties);
				break;
			case "schedules":
				createSchedules(totalToCreate, creationRate, properties);
				break;
			default:
				log.info("not implemented yet for " + entityType);
				break;
		}
	}

	private void pauseForThisBatch(final int index, final int creationRate) {
		if(index % creationRate == 0) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {

			}
		}
	}

	private void pauseForRemainingMs() {
		stopWatch.stop();
		long remaining = 1000 - stopWatch.elapsed(TimeUnit.MILLISECONDS);
		if(remaining > 0) {
			try {
				Thread.sleep(remaining);
			} catch (InterruptedException e) { }
		}
		stopWatch.start();
	}

//	private <T extends CreateTxnFactory> void create(T transaction, final int totalToCreate, final int creationRate, final Properties props) {
//		stopWatch.start();
//		int i = 0;
//
//		while(i < totalToCreate) {
//			int j = 0;
//			try {
//				Transaction txn = T.newCreateTxn()
//						.balance(i * 1_000_000L)
//						.receiverSigRequired(false)
//						.fee(1_000_000_000L)
//						.memo("Memo for account " + i)
//						.get();
//				TransactionResponse resp = ctx.submissionFlow().submit(txn);
//				handleTxnResponse(resp, i, "AccountCreate", txn, totalToCreate / 10);
//			} catch (Throwable e) {
//				log.warn("Something happened while creating accounts: ", e);
//			}
//			i++; j++;
//			if(j >= creationRate) {
//				j = 0;
//				pauseForRemainingMs();
//			}
//		}
//		totalAccounts = totalToCreate;
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) { }
//
//		log.info("Done creating {} accounts", totalToCreate);
//
//	}

	private void createAccounts(final int totalToCreate, final int creationRate) {
		int i = 0;
		while(i < totalToCreate) {
			int j = 0;
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
				log.warn("Something happened while creating accounts: ", e);
			}
			i++; j++;
			if(j >= creationRate) {
				j = 0;
				pauseForRemainingMs();
			}
		}
		totalAccounts = totalToCreate;
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) { }

		log.info("Done creating {} accounts", totalToCreate);
	}

	private void createTopics(final int totalToCreate, final int creationRate, final Properties properties) {
		int i = 0;
		while(i < totalToCreate) {
			int j = 0;
			try {
				Transaction txn = TopicCreateTxnFactory.newSignedConsensusCreateTopic()
						.fee(1_000_000_000L)
						//.payer("0.0." + selectRandomAccount()) to avoid INSUFFICIENT_PAYER_BALANCE issue
						.memo("Memo for topics " + i)
						.get();
				TransactionResponse resp = ctx.submissionFlow().submit(txn);
				handleTxnResponse(resp, i, "TopicCreate", txn, totalToCreate/10);
			} catch (Throwable e) {
				log.warn("Something happened while creating topics: ", e);
			}
			i++; j++;
			if(j >= creationRate) {
				pauseForRemainingMs();
			}
		}
		log.info("Done creating {} topics", totalToCreate);
	}

	private void createTokens(final int totalToCreate, final int creationRate, final Properties properties ) {
		int i = 0;
		while(i < totalToCreate) {
			int j = 0;
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
				log.warn("Something happened while creating fungible common tokens: ", e);
			}
			i++; j++;
			if(j >= creationRate) {
				j = 0;
				pauseForRemainingMs();
			}
		}
		tokenNumStart = currentEntityNumEnd.get();
		totalTokens = totalToCreate;
		log.info("Done creating {} fungible tokens", totalToCreate);
	}

	private void createFiles(final int totalToCreate, final int creationRate,
			final boolean forContractFile,
			final Properties properties ) {
		int i = 0;
		while(i < totalToCreate) {
			int j = 0;
			try {
				Transaction txn = FileCreateTxnFactory.newSignedFileCreate()
						.fee(1_000_000_000L)
						.forContractFile(forContractFile)
						.get();
				TransactionResponse resp = ctx.submissionFlow().submit(txn);
				handleTxnResponse(resp, i, "FileCreate", txn, totalToCreate/10);
			} catch (Throwable e) {
				log.warn("Something happened while creating files: ", e);
			}
			i++; j++;
			if(j >= creationRate) {
				j = 0;
				pauseForRemainingMs();
			}
		}
		if(forContractFile) {
			contractFileNumStart = currentEntityNumEnd.get();
			totalContractFiles = totalToCreate;
		}
		log.info("Done creating {} files", totalToCreate);
	}

	private void createSmartContracts(final int totalToCreate, final int creationRate, final Properties properties ) {
		final int totalContractFile = Integer.parseInt(properties.getProperty("smartContracts.total.file"));
		final int fileCreationRate = Integer.parseInt(properties.getProperty("files.creation.rate"));
		createFiles(totalContractFile, fileCreationRate, true, properties);

		int i = 0;
		while(i < totalToCreate) {
			int j = 0;
			try {
				Transaction txn = ContractCreateTxnFactory.newSignedContractCreate()
						.fee(10_000_000L)
						.initialBalance(100_000_000L)
						.fileID(FileID.newBuilder()
								.setFileNum(selectRandomContractFile()).setRealmNum(0).setShardNum(0)
								.build())
						.payer("0.0.2")
						.gas(5_000_000L)
						.get();
				TransactionResponse resp = ctx.submissionFlow().submit(txn);
				handleTxnResponse(resp, i, "SmartContractCreate", txn, totalToCreate/10);
			} catch (Throwable e) {
				log.warn("Something happened while creating smart contracts: ", e);
			}
			i++; j++;
			if(j >= creationRate) {
				j = 0;
				pauseForRemainingMs();
			}
			pauseForThisBatch(i, creationRate);
		}
		log.info("Done creating {} smart contracts", totalToCreate);
	}

	private void createSchedules(final int totalToCreate, final int creationRate, final Properties properties ) {
		int i = 0;
		while(i < totalToCreate) {
			int j = 0;
			try {
				Transaction txn = ScheduleCreateTxnFactory.newSignedScheduleCreate()
						.fee(1_000_000_000L)
						//.designatingPayer(asAccount("0.0." + selectRandomAccount()))
						.memo("Schedule " + i)
						.from(selectRandomAccount())
						.to(selectRandomAccount())
						.payer("0.0.2")
						.get();
				TransactionResponse resp = ctx.submissionFlow().submit(txn);
				handleTxnResponse(resp, i, "ScheduleCreate", txn, totalToCreate/10);
			} catch (Throwable e) {
				log.warn("Something happened while creating scheduled transactions: ", e);
			}
			i++; j++;
			if(j >= creationRate) {
				j = 0;
				pauseForRemainingMs();
			}
		}
		log.info("Done creating {} schedule transactions", totalToCreate);
	}

	private void createTokenAssociations(final int totalToCreate, final int creationRate, final Properties properties ) {
		int i = 0;
		while(i < totalToCreate) {
			int j = 0;
			try {
				Transaction txn = TokenAssociateCreateTxnFactory.newSignedTokenAssociate()
						.fee(1_000_000_000L)
						.targeting(selectRandomAccount())
						.associating(selectRandomToken())
						.get();
				TransactionResponse resp = ctx.submissionFlow().submit(txn);
				handleTxnResponse(resp, i, "TokenAssociationCreate", txn, totalToCreate/10);
			} catch (Throwable e) {
				log.warn("Something happened while creating token associations: ", e);
			}
			i++; j++;
			if(j >= creationRate) {
				j = 0;
				pauseForRemainingMs();
			}
		}
		try {
			Thread.sleep(60000);
		} catch (InterruptedException e) {	}
		log.info("Done creating {} Token Associations", totalToCreate);
	}

	private void createUniqTokens(final int totalToCreate, final int creationRate, final Properties properties ) {
		int i = 0;
		while(i < totalToCreate) {
			try {
				Transaction txn = UniqTokenCreateTxnFactory.newSignedUniqTokenCreate()
						.fee(1_000_000_000L)
						.name("uniqToken" + i)
						.symbol("UNIQ" + i)

						.treasury(asAccount("0.0." + selectRandomAccount()))
						.get();
				TransactionResponse resp = ctx.submissionFlow().submit(txn);
				handleTxnResponse(resp, i, "TokenCreate", txn, totalToCreate/10);
			} catch (Throwable e) {
				log.warn("Something happened while creating unique tokens: ", e);
			}
			i++;
			pauseForThisBatch(i, creationRate);
		}
		uniqTokenNumStart = currentEntityNumEnd.get();
		totalUniqTokens = totalToCreate;

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) { }

		log.info("Done creating {} uniqTokens", totalToCreate);
	}

	private void createNfts(final int totalToCreate, final int creationRate, final Properties properties ) {
		int nftsPerToken = (int) Math.ceil(totalToCreate / totalUniqTokens);
		if (nftsPerToken > MAX_NFT_PER_TOKEN) {
			log.warn("One token can't have {} NFTs. Max is {}", nftsPerToken, MAX_NFT_PER_TOKEN);
			nftsPerToken = MAX_NFT_PER_TOKEN;
		}
		for (int i = uniqTokenNumStart; i < uniqTokenNumStart + totalUniqTokens; i++) {
			int k = 0;
			try {
				int rounds = nftsPerToken / NFT_MINT_BATCH_SIZE;
				for(int j = 0; j < rounds; j++) {
					mintOneBatchNFTs(i, totalToCreate, NFT_MINT_BATCH_SIZE);
				}
				int remaining = nftsPerToken - rounds * NFT_MINT_BATCH_SIZE;
				if(remaining > 0) {
					mintOneBatchNFTs(i, totalToCreate, remaining);
				}
			} catch (Throwable e) {
				log.warn("Something happened while creating nfts: ", e);
			}
			k++;
			if(k >= creationRate) {
				k = 0;
				pauseForRemainingMs();
			}
		}
		try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {	}
		log.info("Done creating {} NFTs", nftsPerToken * totalUniqTokens);
	}

	private void mintOneBatchNFTs(final int uniqTokenNum, final int totalToCreate,
			final int numOfNfts) throws Throwable {
		Transaction txn = NftCreateTxnFactory.newSignedNftCreate()
				.fee(1_000_000_000L)
				.forUniqToken(uniqTokenNum)
				.metaDataPer(numOfNfts)
				.get();

		TransactionResponse resp = ctx.submissionFlow().submit(txn);
		handleTxnResponse(resp, uniqTokenNum, "NftCreate", txn, totalToCreate / 10);
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
	private int selectRandomContractFile() {
		return contractFileNumStart + random.nextInt(totalContractFiles);
	}
}
