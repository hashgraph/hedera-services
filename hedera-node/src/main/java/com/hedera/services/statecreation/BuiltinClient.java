package com.hedera.services.statecreation;

import com.google.protobuf.InvalidProtocolBufferException;
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
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hedera.services.statecreation.creationtxns.utils.TempUtils.asAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;

public class BuiltinClient implements Runnable {
	static Logger log = LogManager.getLogger(BuiltinClient.class);

	final private static String DEFAULT_ACCT = "0.0.2";
	final private static long FEE_ALLOWED = 100_000_000L;
	final private static long BASE_INIT_BALANCE = 10_000_000_000L;
	final private static int BALANCE_VAR = 1000_000_000;
	final private static long GAS_TO_PAY = 5_000_000;

	final private static int ONE_SECOND = 1000;
	final private static int ONE_MINUTE = 60000;
	final private static int BACK_OFF_MS = 100;

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
	private static int totalRandomFiles;
	private static int contractFileNumStart;
	private static int totalContractFiles;

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
			Thread.sleep(5 * ONE_SECOND);
		} catch (InterruptedException e) {	}

		processOrders.forEach(this::createEntitiesFor);

		log.info("All entities created. Shutdown the client thread");

		try {
			Thread.sleep(ONE_MINUTE);
		} catch (InterruptedException e) {	}

		allCreated.set(true);
		log.info("Current seqNo: " + ctx.seqNo().current());
	}

	private void createEntitiesFor(Integer posi, String entityType) {
		String valStr = properties.getProperty(processOrders.get(posi) + ".total");
		int totalToCreate = Integer.parseInt(valStr);

		if(totalToCreate > 0) {
			log.info("Start to build " + valStr + " " + entityType + ". Starting number: " + currentEntityNumEnd.get());
			createEntitiesFor(entityType,  totalToCreate, properties, processOrders);
			log.info( entityType + " value range [{} - {}]",
					currentEntityNumEnd.get(),currentEntityNumEnd.get() + totalToCreate - 1);

			log.info("Current seqNo: " + ctx.seqNo().current());
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
				createTopics(totalToCreate);
				break;
			case "tokens":
				createTokens(totalToCreate);
				break;
			case "files":
				createFiles(totalToCreate, 0,false);
				break;
			case "smartContracts":
				createSmartContracts(totalToCreate, properties);
				break;
			case "tokenAssociations":
				createTokenAssociations(totalToCreate);
				break;
			case "uniqueTokens":
				createUniqTokens(totalToCreate);
				break;
			case "nfts":
				createNfts(totalToCreate);
				break;
			case "schedules":
				createSchedules(totalToCreate);
				break;
			default:
				log.info("not implemented yet for " + entityType);
				break;
		}
	}

	private static void backOff() {
		try {
			Thread.sleep(BACK_OFF_MS);
		} catch (InterruptedException e) { }
	}

	@FunctionalInterface
	interface CreateTxnThrows<R, T> {
		R create(T t) throws Throwable;
	}

	private int getReportStep(final int totalToCreate) {
		if(totalToCreate > 1_000_000) {
			return totalToCreate / 100;
		} else if(totalToCreate > 100_000) {
			return totalToCreate / 50;
		} else if (totalToCreate > 10) {
			return totalToCreate / 10;
		}
		return totalToCreate;
	}

	private static class FlowControl {
		private HederaFunctionality opType;
		private int maxAllowed;
		private int pending;
		private int reportEvery;
		private int preExisting;
		private ServicesContext ctx;
		public FlowControl(HederaFunctionality opType, int maxWait, int pendingAllowed, int reportEvery,
				int preExisting, final ServicesContext ctx) {
			this.maxAllowed = maxWait;
			this.pending = pendingAllowed;
			this.preExisting = preExisting;
			this.reportEvery = reportEvery;
			this.ctx = ctx;
			this.opType = opType;
		}

		public void shallWaitHere(final int totalSubmitted) {
			int count = 0;
			while((ctx.opCounters().handledSoFar(opType) < totalSubmitted - pending - preExisting)
					&& count < maxAllowed) {
				count++;
				if(count % reportEvery == 0) {
					log.info("Pause for transaction type {} handled: {}, submitted = {}", opType,
							ctx.opCounters().handledSoFar(opType), totalSubmitted);
				}
				backOff();
			}
		}
	}

	private boolean submitOneTxn(final Transaction txn,
			final HederaFunctionality txnType,
			final int index) throws Throwable {
		SignedTxnAccessor accessor = new SignedTxnAccessor(txn);
		ResponseCodeEnum responseCode = ctx.submissionManager().trySubmission(accessor);

		if(responseCode != OK) {
			log.info("Response code is {} for {} txn #{} and body {}",
					responseCode , txnType, index, txn.toString());
			return false;
		}
		return true;
	}

	private void createWithFlowControl(CreateTxnThrows<Transaction, Integer> createOne,
			final int totalToCreate,
			final HederaFunctionality txnType,
			final int reportSteps,
			final Optional<FlowControl> flowControl) {

		int reportEvery;
		if(reportSteps > 0) {
			reportEvery = totalToCreate / reportSteps;
		} else {
			reportEvery = getReportStep(totalToCreate);
		}
		int i = 0;
		while(i < totalToCreate) {
			int j = 0;
			try {
				Transaction txn = createOne.create(i);
				if(submitOneTxn(txn, txnType, i)) {
					i++;
					if(i % reportEvery  == 0 ) {
						log.info("Successfully submitted {} txn #{}, handled so far: {} ", txnType, i,
								ctx.opCounters().handledSoFar(txnType));
						log.info("Current seqNo: " + ctx.seqNo().current());
					}
				}
				else {
					log.info("Failed submit for transaction type {},  handled so far: {}",
							txnType, ctx.opCounters().handledSoFar(txnType));
				}
			} catch (InvalidProtocolBufferException e) {
				log.warn("Bad transaction body for {}: ", txnType, e);
			} catch (Throwable e) {
				log.warn("Possible invalid signature for transaction {}: ", txnType, e);
			}
			if(flowControl.isPresent()) {
				flowControl.get().shallWaitHere(i);
			}
		}
	}

	private void createAccounts(final int totalToCreate) {
		CreateTxnThrows<Transaction, Integer> createAccount = (Integer i) -> {
			Transaction txn = CryptoCreateTxnFactory.newSignedCryptoCreate()
					.balance(BASE_INIT_BALANCE + random.nextInt(BALANCE_VAR))
					.receiverSigRequired(false)
					.fee(FEE_ALLOWED)
					.memo("Memo for account " + i)
					.get();
			return txn;
		};

		FlowControl acctFlowControl = new FlowControl(CryptoCreate,
				2000,1000,10, 0, ctx);
		createWithFlowControl (createAccount, totalToCreate, CryptoCreate,
				0, Optional.of(acctFlowControl));

		totalAccounts = totalToCreate;

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) { }

		log.info("Done creating {} accounts", totalToCreate);
	}

	private void createTopics(final int totalToCreate) {
		CreateTxnThrows<Transaction, Integer> createTopic = (Integer i) -> {
			Transaction txn = TopicCreateTxnFactory.newSignedConsensusCreateTopic()
					.fee(FEE_ALLOWED)
					.payer("0.0." + selectRandomAccount())
					.memo("Memo for topics " + i)
					.get();
			return txn;
		};

		FlowControl topicFlowControl = new FlowControl(ConsensusCreateTopic,
				1000,2000,10, 0, ctx);
		createWithFlowControl (createTopic, totalToCreate, ConsensusCreateTopic,
				0, Optional.of(topicFlowControl));
		log.info("Done creating {} topics", totalToCreate);
	}

	private void createTokens(final int totalToCreate) {
		CreateTxnThrows<Transaction, Integer> createToken = (Integer i) -> {
			Transaction txn = TokenCreateTxnFactory.newSignedTokenCreate()
					.fee(FEE_ALLOWED)
					.name("token" + i)
					.symbol("SYMBOL" + i)
					.treasury(asAccount("0.0." + selectRandomAccount()))
					.get();
			return txn;
		};

		FlowControl tokenFlowControl = new FlowControl(TokenCreate,
				1000,2000,10, 0, ctx);
		createWithFlowControl(createToken, totalToCreate, TokenCreate,
				100, Optional.of(tokenFlowControl));

		tokenNumStart = currentEntityNumEnd.get();
		totalTokens = totalToCreate;
		log.info("Done creating {} fungible tokens", totalToCreate);
	}

	private void createFiles(final int totalToCreate, final int preExisting,
			final boolean forContractFile) {

		CreateTxnThrows<Transaction, Integer> createFile = (Integer i) -> {
			Transaction txn = FileCreateTxnFactory.newSignedFileCreate()
					.fee(FEE_ALLOWED)
					.forContractFile(forContractFile)
					.get();
			return txn;
		};

		FlowControl fileFlowControl = new FlowControl(FileCreate,
				5000,100,10, preExisting, ctx);
		createWithFlowControl(createFile, totalToCreate,  FileCreate,
				10, Optional.of(fileFlowControl));

		if(forContractFile) {
			contractFileNumStart = currentEntityNumEnd.get();
			totalContractFiles = totalToCreate;
		}
		totalRandomFiles = totalToCreate;
		log.info("Done creating {} files", totalToCreate);
	}

	private void createSmartContracts(final int totalToCreate,
			final Properties properties ) {
		final int totalContractFile = Integer.parseInt(properties.getProperty("smartContracts.total.file"));
		createFiles(totalContractFile, totalRandomFiles, true);

		CreateTxnThrows<Transaction, Integer> createContract = (Integer i) -> {
			Transaction txn = ContractCreateTxnFactory.newSignedContractCreate()
					.fee(FEE_ALLOWED)
					.initialBalance(BASE_INIT_BALANCE)
					.fileID(FileID.newBuilder()
							.setFileNum(selectRandomContractFile()).setRealmNum(0).setShardNum(0)
							.build())
					.payer(DEFAULT_ACCT)
					.gas(GAS_TO_PAY)
					.get();
			return txn;
		};

		FlowControl contractFlowControl = new FlowControl(ContractCreate,
				5000,100,10, 0, ctx);
		createWithFlowControl(createContract, totalToCreate, ContractCreate,
				10, Optional.of(contractFlowControl));
		log.info("Done creating {} smart contracts", totalToCreate);
	}

	private void createSchedules(final int totalToCreate) {
		CreateTxnThrows<Transaction, Integer> createSchedule = (Integer i) -> {
			Transaction txn = ScheduleCreateTxnFactory.newSignedScheduleCreate()
					.fee(FEE_ALLOWED)
					.designatingPayer(asAccount("0.0." + selectRandomAccount()))
					.memo("Schedule " + i)
					.from(selectRandomAccount())
					.to(selectRandomAccount())
					.payer(DEFAULT_ACCT)
					.get();
			return txn;
		};

		FlowControl scheduleFlowControl = new FlowControl(ScheduleCreate,
				5000,500,50, 0, ctx);
		createWithFlowControl(createSchedule, totalToCreate, ScheduleCreate,
				50, Optional.of(scheduleFlowControl));

		log.info("Done creating {} schedule transactions", totalToCreate);
	}

	private void createTokenAssociations(final int totalToCreate//, final int creationRate
	) {
		CreateTxnThrows<Transaction, Integer> createTokenAssociation = (Integer i) -> {
			Transaction txn = TokenAssociateCreateTxnFactory.newSignedTokenAssociate()
					.fee(FEE_ALLOWED)
					.targeting(selectRandomAccount())
					.associating(selectRandomToken())
					.get();
			return txn;
		};
		FlowControl tokenAssoFlowControl = new FlowControl(TokenAssociateToAccount,
				5000,1000,100, 0, ctx);
		createWithFlowControl(createTokenAssociation, totalToCreate,
				TokenAssociateToAccount, 0, Optional.of(tokenAssoFlowControl));

		log.info("Successfully submitted {} tokenAssociation txns, handled so far: {}",
				totalToCreate, ctx.opCounters().handledSoFar(TokenAssociateToAccount));

		log.info("Done creating {} Token Associations", totalToCreate);
	}

	private void createUniqTokens(final int totalToCreate) {
		CreateTxnThrows<Transaction, Integer> createUniqueToken = (Integer i) -> {
			Transaction txn = UniqTokenCreateTxnFactory.newSignedUniqTokenCreate()
					.fee(FEE_ALLOWED)
					.name("uniqToken" + i)
					.symbol("UNIQ" + i)
					.treasury(asAccount("0.0." + selectRandomAccount()))
					.get();
			return txn;
		};

		FlowControl uniqTokenFlowControl = new FlowControl(TokenCreate,
				10000,1000,10, totalTokens, ctx);
		createWithFlowControl(createUniqueToken, totalToCreate, TokenCreate,
				10, Optional.of(uniqTokenFlowControl));

		uniqTokenNumStart = currentEntityNumEnd.get();
		totalUniqTokens = totalToCreate;

		log.info("Total token  handled so far: {}", ctx.opCounters().handledSoFar(TokenCreate));

		log.info("Done creating {} uniqTokens", totalToCreate);
	}

	private void createNfts(int totalToCreate) {
		int nftsPerToken = (int) Math.ceil(totalToCreate / totalUniqTokens);
		if (nftsPerToken > MAX_NFT_PER_TOKEN) {
			log.warn("One token can't have {} NFTs. Max is {}", nftsPerToken, MAX_NFT_PER_TOKEN);
			nftsPerToken = MAX_NFT_PER_TOKEN;
		}
		totalToCreate = nftsPerToken * totalUniqTokens;
		log.info("NFTs per uniq token: {}", nftsPerToken );
		log.info("Will mint total {} NFTs for {} unique tokens", totalToCreate, totalUniqTokens);

		int totalMintTimes;
		int batchSize;
		if(nftsPerToken > NFT_MINT_BATCH_SIZE) {
			totalMintTimes = (int)Math.ceil(nftsPerToken / NFT_MINT_BATCH_SIZE) * totalUniqTokens;
			batchSize = NFT_MINT_BATCH_SIZE;
		} else {
			totalMintTimes = totalUniqTokens;
			batchSize = 1;
		}

		int reportEvery = totalUniqTokens / 100;

		createAllNfts(nftsPerToken, batchSize, reportEvery);

		// final wait to give platform time to clear wait queue.
		log.info("Wait for platform to consume excessive mint operation before moving to next action");
		FlowControl nftFinalWait = new FlowControl(TokenMint,
				50000, 1000, 100, 0, ctx);
		nftFinalWait.shallWaitHere(totalMintTimes);
		log.info("Successfully submitted {} {} for unique token #{} with {} NFTs, handled so far: {}",
				totalMintTimes, TokenMint, totalUniqTokens, nftsPerToken, ctx.opCounters().handledSoFar(TokenMint));

		log.info("Done creating {} NFTs", nftsPerToken * totalUniqTokens);
	}

	private void createAllNfts(final int nftsPerToken,
			final int batchSize,
			final int reportEvery) {
		FlowControl nftCreationFlowControl = new FlowControl(TokenMint,
				5000, 100, 10, 0, ctx);
		for (int i = 0; i < totalUniqTokens; i++) {
			try {
				int rounds = nftsPerToken / batchSize;
				int j = 0;
				while(j < rounds) {
					if(mintOneBatchNFTs(i, nftsPerToken, j, batchSize, 0)) {
						j++;
					}
				}
				int remaining = nftsPerToken - rounds * batchSize;
				if(remaining > 0) {
					log.info("Submit remaining {} NFTs creation for unique token #{}", remaining, i);
					boolean success = false;
					do {
						success = mintOneBatchNFTs(i, nftsPerToken, j, batchSize, remaining);
					} while (success == false);
				}
			} catch (Throwable e) {
				log.warn("Something happened while creating nfts: ", e);
			}

			int mintSubmitted = i * nftsPerToken / batchSize;

			nftCreationFlowControl.shallWaitHere(mintSubmitted);

			if(i % reportEvery == 0) {
				log.info("Successfully submitted {} {} for unique token #{} with {} NFTs, handled so far: {}",
						mintSubmitted, TokenMint, i, nftsPerToken, ctx.opCounters().handledSoFar(TokenMint));
			}
		}
	}

	private boolean mintOneBatchNFTs(final int i,
			final int nftsPerToken,
			final int round,
			final int batchSize,
			final int remaining) throws Throwable {
		Transaction txn = NftCreateTxnFactory.newSignedNftCreate()
				.fee(FEE_ALLOWED)
				.forUniqToken(i + uniqTokenNumStart)
				.metaDataPer(batchSize)
				.get();
		int nftsSubmittedSofar = i * nftsPerToken + (round + 1) * batchSize + remaining;
		return submitOneTxn(txn, TokenMint, (int)Math.ceil(nftsSubmittedSofar / batchSize));
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
