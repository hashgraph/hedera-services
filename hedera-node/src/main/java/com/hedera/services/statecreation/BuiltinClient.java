package com.hedera.services.statecreation;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.statecreation.creationtxns.ContractCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.CryptoCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.NftCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.ScheduleCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.TokenAssociateCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.TokenCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.TopicCreateTxnFactory;
import com.hedera.services.statecreation.creationtxns.FileCreateTxnFactory;
import com.hedera.services.txns.submission.PlatformSubmissionManager;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Properties;
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
	private static Logger log = LogManager.getLogger(BuiltinClient.class);

	private static final String DEFAULT_ACCT = "0.0.2";
	private static final long FEE_ALLOWED = 100_000_000L;
	private static final long BASE_INIT_BALANCE = 10_000_000_000L;
	private static final int BALANCE_VAR = 1000_000_000;
	private static final long GAS_TO_PAY = 5_000_000;

	private static final long ONE_SECOND = 1000L;
	private static final long ONE_MINUTE = 60000;
	private static final long BACK_OFF_MS = 100;

	private static final int MAX_NFT_PER_TOKEN = 1000;
	private static final int NFT_MINT_BATCH_SIZE = 10;

	private static final int SYSTEM_ACCOUNTS = 1000;
	private final Map<Integer, String> processOrders;
	private final Properties properties;
	private final AtomicBoolean allCreated;
	private final PlatformSubmissionManager submissionManager;
	private final NetworkCtxManager networkCtxManager;

	private final SecureRandom random = new SecureRandom();

	private int totalAccounts;
	private int tokenNumStart;
	private int totalTokens;
	private int uniqTokenNumStart;
	private int totalUniqTokens;
	private int totalRandomFiles;
	private int contractFileNumStart;
	private int totalContractFiles;

	private static AtomicInteger currentEntityNumEnd = new AtomicInteger(1001);

	public BuiltinClient(final Properties layoutProps,
			final Map<Integer, String> processOrders,
			final PlatformSubmissionManager submissionManager,
			final NetworkCtxManager networkCtxManager,
			final AtomicBoolean allCreated) {
		this.processOrders = processOrders;
		this.properties = layoutProps;
		this.submissionManager = submissionManager;
		this.networkCtxManager = networkCtxManager;
		this.allCreated = allCreated;
	}

	@Override
	public void run() {
		log.info("In built client, wait for server to start up from genesis...");
		try {
			Thread.sleep(5 * ONE_SECOND);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		processOrders.forEach(this::createEntitiesFor);

		log.info("All entities created. Shutdown the client thread");

		try {
			Thread.sleep(ONE_MINUTE);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		allCreated.set(true);
	}

	private void createEntitiesFor(Integer posi, String entityType) {
		String valStr = properties.getProperty(processOrders.get(posi) + ".total");
		int totalToCreate = Integer.parseInt(valStr);

		if(totalToCreate > 0) {
			log.info("Start to build {} {}. Starting number: {}", valStr, entityType, currentEntityNumEnd.get());
			createEntitiesFor(entityType,  totalToCreate, properties);
			log.info(  "{} value range [{} - {}]",entityType,
					currentEntityNumEnd.get(),currentEntityNumEnd.get() + totalToCreate - 1);

			log.info("SeqNo is {} after creating {}",
					networkCtxManager.networkContext().seqNo().current(), entityType);
			currentEntityNumEnd.set(currentEntityNumEnd.get() + totalToCreate);
		}
	}

	private void createEntitiesFor(final String entityType,
			final int totalToCreate,
			final Properties properties) {

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
				log.info("not implemented yet for {}", entityType);
				break;
		}
	}

	@FunctionalInterface
	interface CreateTxnThrows<R, T> {
		R create(T t) throws IllegalStateException;
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
		private NetworkCtxManager networkCtxManager;


		public FlowControl(HederaFunctionality opType, int maxWait, int pendingAllowed, int reportEvery,
		int preExisting, final NetworkCtxManager networkCtxManager) {
			this.maxAllowed = maxWait;
			this.pending = pendingAllowed;
			this.preExisting = preExisting;
			this.reportEvery = reportEvery;
			this.networkCtxManager = networkCtxManager;
			this.opType = opType;
		}

		public void shallWaitHere(final int totalSubmitted) {
			int count = 0;
			while((networkCtxManager.opCounters().handledSoFar(opType) < totalSubmitted - pending - preExisting)
					&& count < maxAllowed) {
				count++;
				if(count % reportEvery == 0) {
					log.info("Pause for transaction type {} handled: {}, submitted = {}", opType,
							networkCtxManager.opCounters().handledSoFar(opType), totalSubmitted);
				}
				backOff();
			}
		}

		private static void backOff() {
			try {
				Thread.sleep(BACK_OFF_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private boolean submitOneTxn(final Transaction txn,
			final HederaFunctionality txnType,
			final int index) throws InvalidProtocolBufferException {
		SignedTxnAccessor accessor = new SignedTxnAccessor(txn);
		ResponseCodeEnum responseCode = submissionManager.trySubmission(accessor);

		if(responseCode != OK) {
			log.info("Response code is {} for {} txn #{} and body {}",
					responseCode , txnType, index, txn);
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
			try {
				Transaction txn = createOne.create(i);
				if(submitOneTxn(txn, txnType, i)) {
					i++;
					if(i % reportEvery  == 0 ) {
						log.info("Successfully submitted {} txn #{}, handled so far: {}. Current SeqNo = {} ",
								txnType, i,	networkCtxManager.opCounters().handledSoFar(txnType),
								networkCtxManager.networkContext().seqNo().current());
					}
				}
				else {
					log.info("Failed submit for transaction type {},  handled so far: {}",
							txnType, networkCtxManager.opCounters().handledSoFar(txnType));
				}
			} catch (InvalidProtocolBufferException e) {
				log.warn("Bad transaction body for {}: ", txnType, e);
			} catch (IllegalStateException e) {
				log.warn("Possible invalid signature for transaction {}: ", txnType, e);
			}
			if(flowControl.isPresent()) {
				flowControl.get().shallWaitHere(i);
			}
		}
	}

	private void createAccounts(final int totalToCreate) {
		CreateTxnThrows<Transaction, Integer> createAccount = i ->
			CryptoCreateTxnFactory.newSignedCryptoCreate()
					.balance(BASE_INIT_BALANCE + random.nextInt(BALANCE_VAR))
					.receiverSigRequired(false)
					.fee(FEE_ALLOWED)
					.memo("Memo for account " + i)
					.get();


		FlowControl acctFlowControl = new FlowControl(CryptoCreate,
				2000,1000,10, 0, networkCtxManager);
		createWithFlowControl (createAccount, totalToCreate, CryptoCreate,
				0, Optional.of(acctFlowControl));

		totalAccounts = totalToCreate;

		try {
			Thread.sleep(10 * ONE_SECOND);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		log.info("Done creating {} accounts", totalToCreate);
	}

	private void createTopics(final int totalToCreate) {
		CreateTxnThrows<Transaction, Integer> createTopic = (Integer i) ->
			TopicCreateTxnFactory.newSignedConsensusCreateTopic()
					.fee(FEE_ALLOWED)
					.payer("0.0." + selectRandomAccount())
					.memo("Memo for topics " + i)
					.get();

		FlowControl topicFlowControl = new FlowControl(ConsensusCreateTopic,
				1000,2000,10, 0, networkCtxManager);
		createWithFlowControl (createTopic, totalToCreate, ConsensusCreateTopic,
				0, Optional.of(topicFlowControl));
		log.info("Done creating {} topics", totalToCreate);
	}

	private void createTokens(final int totalToCreate) {
		CreateTxnThrows<Transaction, Integer> createToken = i ->
			TokenCreateTxnFactory.newSignedTokenCreate()
					.fee(FEE_ALLOWED)
					.name("token" + i)
					.symbol("SYMBOL" + i)
					.treasury(asAccount("0.0." + selectRandomAccount()))
					.get();

		FlowControl tokenFlowControl = new FlowControl(TokenCreate,
				1000,2000,10, 0, networkCtxManager);
		createWithFlowControl(createToken, totalToCreate, TokenCreate,
				100, Optional.of(tokenFlowControl));

		tokenNumStart = currentEntityNumEnd.get();
		totalTokens = totalToCreate;
		log.info("Done creating {} fungible tokens", totalToCreate);
	}

	private void createFiles(final int totalToCreate, final int preExisting,
			final boolean forContractFile) {

		CreateTxnThrows<Transaction, Integer> createFile = i ->
			FileCreateTxnFactory.newSignedFileCreate()
					.fee(FEE_ALLOWED)
					.forContractFile(forContractFile)
					.get();

		FlowControl fileFlowControl = new FlowControl(FileCreate,
				5000,100,10, preExisting, networkCtxManager);
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

		CreateTxnThrows<Transaction, Integer> createContract = i ->
			ContractCreateTxnFactory.newSignedContractCreate()
					.fee(FEE_ALLOWED)
					.initialBalance(BASE_INIT_BALANCE)
					.fileID(FileID.newBuilder()
							.setFileNum(selectRandomContractFile()).setRealmNum(0).setShardNum(0)
							.build())
					.payer(DEFAULT_ACCT)
					.gas(GAS_TO_PAY)
					.get();

		FlowControl contractFlowControl = new FlowControl(ContractCreate,
				5000,100,10, 0, networkCtxManager);
		createWithFlowControl(createContract, totalToCreate, ContractCreate,
				10, Optional.of(contractFlowControl));
		log.info("Done creating {} smart contracts", totalToCreate);
	}

	private void createSchedules(final int totalToCreate) {
		CreateTxnThrows<Transaction, Integer> createSchedule = i ->
			ScheduleCreateTxnFactory.newSignedScheduleCreate()
					.fee(FEE_ALLOWED)
					.designatingPayer(asAccount("0.0." + selectRandomAccount()))
					.memo("Schedule " + i)
					.from(selectRandomAccount())
					.to(selectRandomAccount())
					.payer(DEFAULT_ACCT)
					.get();

		FlowControl scheduleFlowControl = new FlowControl(ScheduleCreate,
				5000,500,50, 0, networkCtxManager);
		createWithFlowControl(createSchedule, totalToCreate, ScheduleCreate,
				50, Optional.of(scheduleFlowControl));

		log.info("Done creating {} schedule transactions", totalToCreate);
	}

	private void createTokenAssociations(final int totalToCreate) {
		CreateTxnThrows<Transaction, Integer> createTokenAssociation = i ->
			TokenAssociateCreateTxnFactory.newSignedTokenAssociate()
					.fee(FEE_ALLOWED)
					.targeting(selectRandomAccount())
					.associating(selectRandomToken())
					.get();
		FlowControl tokenAssoFlowControl = new FlowControl(TokenAssociateToAccount,
				5000,1000,100, 0, networkCtxManager);
		createWithFlowControl(createTokenAssociation, totalToCreate,
				TokenAssociateToAccount, 0, Optional.of(tokenAssoFlowControl));

		log.info("Done creating {} Token Associations", totalToCreate);
	}

	private void createUniqTokens(final int totalToCreate) {
		CreateTxnThrows<Transaction, Integer> createUniqueToken = i ->
			TokenCreateTxnFactory.newSignedTokenCreate()
					.fee(FEE_ALLOWED)
					.unique(true)
					.name("uniqToken" + i)
					.symbol("UNIQ" + i)
					.treasury(asAccount("0.0." + selectRandomAccount()))
					.get();

		FlowControl uniqTokenFlowControl = new FlowControl(TokenCreate,
				10000,1000,10, totalTokens, networkCtxManager);
		createWithFlowControl(createUniqueToken, totalToCreate, TokenCreate,
				10, Optional.of(uniqTokenFlowControl));

		uniqTokenNumStart = currentEntityNumEnd.get();
		totalUniqTokens = totalToCreate;

		log.info("Done creating {} unique tokens", totalToCreate);
	}

	private void createNfts(int totalToCreate) {
		int nftsPerToken = (int) Math.ceil((double)totalToCreate / totalUniqTokens);
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
			totalMintTimes = (int)Math.ceil((double)nftsPerToken / NFT_MINT_BATCH_SIZE) * totalUniqTokens;
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
				50000, 1000, 100, 0, networkCtxManager);
		nftFinalWait.shallWaitHere(totalMintTimes);
		log.info("Successfully submitted {} {} for unique token #{} with {} NFTs, handled so far: {}",
				totalMintTimes, TokenMint, totalUniqTokens, nftsPerToken,
				networkCtxManager.opCounters().handledSoFar(TokenMint));

		log.info("Done creating {} NFTs", nftsPerToken * totalUniqTokens);
	}

	private void createAllNfts(final int nftsPerToken,
			final int batchSize,
			final int reportEvery) {
		FlowControl nftCreationFlowControl = new FlowControl(TokenMint,
				5000, 100, 10, 0, networkCtxManager);
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
					//log.info("Submit remaining {} NFTs creation for unique token #{}", remaining, i);
					boolean success = false;
					do {
						success = mintOneBatchNFTs(i, nftsPerToken, j, batchSize, remaining);
					} while (!success);
				}
			} catch (InvalidProtocolBufferException e) {
				log.warn("Bad transaction body for {}: ", TokenMint, e);
			} catch (IllegalStateException e) {
				log.warn("Possible invalid signature for transaction {}: ", TokenMint, e);
			}

			int mintSubmitted = i * nftsPerToken / batchSize;
			nftCreationFlowControl.shallWaitHere(mintSubmitted);
			if(i % reportEvery == 0) {
				log.info("Successfully submitted {} {} for unique token #{} with {} NFTs, handled so far: {}",
						mintSubmitted, TokenMint, i, nftsPerToken,
						networkCtxManager.opCounters().handledSoFar(TokenMint));
			}
		}
	}

	private boolean mintOneBatchNFTs(final int i,
			final int nftsPerToken,
			final int round,
			final int batchSize,
			final int remaining) throws IllegalStateException, InvalidProtocolBufferException {
		Transaction txn = NftCreateTxnFactory.newSignedNftCreate()
				.fee(FEE_ALLOWED)
				.forUniqToken( (long)i + uniqTokenNumStart)
				.metaDataPer(batchSize)
				.get();
		int nftsSubmittedSofar = i * nftsPerToken + (round + 1) * batchSize + remaining;
		return submitOneTxn(txn, TokenMint, (int)Math.ceil((double)nftsSubmittedSofar / batchSize));
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
