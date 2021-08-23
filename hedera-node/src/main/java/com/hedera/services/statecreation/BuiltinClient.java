package com.hedera.services.statecreation;

import com.google.common.base.Stopwatch;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.statecreation.creationtxns.ContractCreateTxnFactory;
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

	private final static int ONE_SECOND = 1000; // ms

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
		log.info("Current seqNo: " + ctx.seqNo().current());
	}

	private void createEntitiesFor(Integer posi, String entityType) {
		String valStr = properties.getProperty(processOrders.get(posi) + ".total");
		int totalToCreate = Integer.parseInt(valStr);
		valStr = properties.getProperty(entityType + ".creation.rate");
		log.info("Create " + totalToCreate + " : " + entityType + ", rate: " + valStr);

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
				createTopics(totalToCreate, creationRate);
				break;
			case "tokens":
				createTokens(totalToCreate, creationRate);
				break;
			case "files":
				createFiles(totalToCreate, creationRate, false);
				break;
			case "smartContracts":
				createSmartContracts(totalToCreate, creationRate, properties);
				break;
			case "tokenAssociations":
				createTokenAssociations(totalToCreate, creationRate);
				break;
			case "uniqueTokens":
				createUniqTokens(totalToCreate, creationRate);
				break;
			case "nfts":
				createNfts(totalToCreate, creationRate);
				break;
			case "schedules":
				createSchedules(totalToCreate, creationRate);
				break;
			default:
				log.info("not implemented yet for " + entityType);
				break;
		}
	}

	private void pauseForRemainingMs() {
		stopWatch.stop();
		long remaining = ONE_SECOND - stopWatch.elapsed(TimeUnit.MILLISECONDS);
		if(remaining > 0) {
			try {
				Thread.sleep(remaining);
			} catch (InterruptedException e) { }
		}
		stopWatch.start();
	}

	@FunctionalInterface
	interface CreateTxnThrows<R, T> {
		R create(T t) throws Throwable;
	}

	private <T> void create(CreateTxnThrows<Transaction, Integer> createOne,
			final int totalToCreate,
			final int creationRate,
			final String txnType,
			final int reportSteps) {
		int i = 0;
		int start = (int)ctx.seqNo().current();
		while(i < totalToCreate) {
			int j = 0;
			try {
				Transaction txn = createOne.create(i);
				TransactionResponse resp = ctx.submissionFlow().submit(txn);
				handleTxnResponse(resp, i, txnType, txn, totalToCreate / reportSteps);
			} catch (Throwable e) {
				log.warn("Something happened while creating {}: ", txnType, e);
			}
			i++;
			j++;
			if(j >= creationRate) {
				j = 0;
				pauseForRemainingMs();
			}
		}
//		int end = (int)ctx.seqNo().current();
//		int waitTimes = 60;
//		while(end != start + totalToCreate && waitTimes > 0) {
//			try {
//				Thread.sleep(1000);
//				waitTimes--;
//			} catch (InterruptedException e) {
//
//			}
//		}
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

	private void createAccounts(final int totalToCreate, final int creationRate) {
		CreateTxnThrows<Transaction, Integer> createAccount = (Integer i) -> {
			Transaction txn = CryptoCreateTxnFactory.newSignedCryptoCreate()
					.balance(i * 1_000_000L)
					.receiverSigRequired(false)
					.fee(1_000_000_000L)
					.memo("Memo for account " + i)
					.get();
			return txn;
		};

		create( createAccount, totalToCreate, creationRate, "createAccount", 10 );

		totalAccounts = totalToCreate;
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) { }

		log.info("Done creating {} accounts", totalToCreate);
	}

	private void createTopics(final int totalToCreate, final int creationRate) {

		CreateTxnThrows<Transaction, Integer> createTopic = (Integer i) -> {
			Transaction txn = TopicCreateTxnFactory.newSignedConsensusCreateTopic()
					.fee(1_000_000_000L)
					//.payer("0.0." + selectRandomAccount()) to avoid INSUFFICIENT_PAYER_BALANCE issue
					.memo("Memo for topics " + i)
					.get();
			return txn;
		};

		create(createTopic, totalToCreate, creationRate, "createTopic", 10);

		log.info("Done creating {} topics", totalToCreate);
	}

	private void createTokens(final int totalToCreate, final int creationRate) {
		CreateTxnThrows<Transaction, Integer> createToken = (Integer i) -> {
			Transaction txn = TokenCreateTxnFactory.newSignedTokenCreate()
					.fee(1_000_000_000L)
					.name("token" + i)
					.symbol("SYMBOL" + i)
					.treasury(asAccount("0.0." + selectRandomAccount()))
					.get();
			return txn;
		};

		create(createToken, totalToCreate, creationRate, "createToken", 10);
		tokenNumStart = currentEntityNumEnd.get();
		totalTokens = totalToCreate;
		log.info("Done creating {} fungible tokens", totalToCreate);
	}

	private void createFiles(final int totalToCreate, final int creationRate,
			final boolean forContractFile) {

		CreateTxnThrows<Transaction, Integer> createFile = (Integer i) -> {
			Transaction txn = FileCreateTxnFactory.newSignedFileCreate()
					.fee(1_000_000_000L)
					.forContractFile(forContractFile)
					.get();
			return txn;
		};

		create(createFile, totalToCreate, creationRate, "createFile", 10);

		if(forContractFile) {
			contractFileNumStart = currentEntityNumEnd.get();
			totalContractFiles = totalToCreate;
		}
		log.info("Done creating {} files", totalToCreate);
	}

	private void createSmartContracts(final int totalToCreate, final int creationRate, final Properties properties ) {
		final int totalContractFile = Integer.parseInt(properties.getProperty("smartContracts.total.file"));
		final int fileCreationRate = Integer.parseInt(properties.getProperty("files.creation.rate"));
		createFiles(totalContractFile, fileCreationRate, true);

		CreateTxnThrows<Transaction, Integer> createContract = (Integer i) -> {
			Transaction txn = ContractCreateTxnFactory.newSignedContractCreate()
					.fee(10_000_000L)
					.initialBalance(100_000_000L)
					.fileID(FileID.newBuilder()
							.setFileNum(selectRandomContractFile()).setRealmNum(0).setShardNum(0)
							.build())
					.payer("0.0.2")
					.gas(5_000_000L)
					.get();
			return txn;
		};
		create(createContract, totalToCreate, creationRate, "createContract", 10);
		log.info("Done creating {} smart contracts", totalToCreate);
	}

	private void createSchedules(final int totalToCreate, final int creationRate) {
		CreateTxnThrows<Transaction, Integer> createSchedule = (Integer i) -> {
			Transaction txn = ScheduleCreateTxnFactory.newSignedScheduleCreate()
					.fee(1_000_000_000L)
					//.designatingPayer(asAccount("0.0." + selectRandomAccount()))
					.memo("Schedule " + i)
					.from(selectRandomAccount())
					.to(selectRandomAccount())
					.payer("0.0.2")
					.get();
			return txn;
		};

		create(createSchedule, totalToCreate, creationRate, "createSchedule", 10);
		log.info("Done creating {} schedule transactions", totalToCreate);
	}

	private void createTokenAssociations(final int totalToCreate, final int creationRate) {
		CreateTxnThrows<Transaction, Integer> createTokenAssociation = (Integer i) -> {
			Transaction txn = TokenAssociateCreateTxnFactory.newSignedTokenAssociate()
					.fee(1_000_000_000L)
					.targeting(selectRandomAccount())
					.associating(selectRandomToken())
					.get();
			return txn;
		};

		create(createTokenAssociation, totalToCreate, creationRate, "createTokenAssociation", 10);

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {	}
		log.info("Done creating {} Token Associations", totalToCreate);
	}

	private void createUniqTokens(final int totalToCreate, final int creationRate) {
		CreateTxnThrows<Transaction, Integer> createUniqueToken = (Integer i) -> {
			Transaction txn = UniqTokenCreateTxnFactory.newSignedUniqTokenCreate()
					.fee(1_000_000_000L)
					.name("uniqToken" + i)
					.symbol("UNIQ" + i)
					.treasury(asAccount("0.0." + selectRandomAccount()))
					.get();
			return txn;
		};

		create(createUniqueToken, totalToCreate, creationRate, "createUniqueToken", 10);

		uniqTokenNumStart = currentEntityNumEnd.get();
		totalUniqTokens = totalToCreate;

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) { }

		log.info("Done creating {} uniqTokens", totalToCreate);
	}

	private void createNfts(final int totalToCreate, final int creationRate) {
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
			Thread.sleep(60000);
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
		handleTxnResponse(resp, uniqTokenNum, "createNFTs", txn, totalToCreate / 10);
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
