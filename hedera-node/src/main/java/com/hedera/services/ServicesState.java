package com.hedera.services;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.migration.KvPairIterationMigrator;
import com.hedera.services.state.migration.ReleaseTwentyFiveMigration;
import com.hedera.services.state.migration.ReleaseTwentySevenMigration;
import com.hedera.services.state.migration.ReleaseTwentySixMigration;
import com.hedera.services.state.migration.StateChildIndices;
import com.hedera.services.state.org.StateMetadata;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.state.virtual.VirtualMapFactory.JasperDbBuilderFactory;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.AbstractNaryMerkleInternal;
import com.swirlds.common.system.AddressBook;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.transaction.SwirldTransaction;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.context.AppsManager.APPS;
import static com.hedera.services.state.migration.StateChildIndices.NUM_POST_0210_CHILDREN;
import static com.hedera.services.state.migration.StateChildIndices.NUM_POST_0270_CHILDREN;
import static com.hedera.services.state.migration.StateVersions.CURRENT_VERSION;
import static com.hedera.services.state.migration.StateVersions.MINIMUM_SUPPORTED_VERSION;
import static com.hedera.services.state.migration.StateVersions.RELEASE_025X_VERSION;
import static com.hedera.services.state.migration.StateVersions.RELEASE_0260_VERSION;
import static com.hedera.services.state.migration.StateVersions.RELEASE_0270_VERSION;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;

/**
 * The Merkle tree root of the Hedera Services world state.
 */
public class ServicesState extends AbstractNaryMerkleInternal implements SwirldState.SwirldState2 {
	private static final Logger log = LogManager.getLogger(ServicesState.class);

	private static final long RUNTIME_CONSTRUCTABLE_ID = 0x8e300b0dfdafbb1aL;
	public static final ImmutableHash EMPTY_HASH = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

	private static boolean expiryJustEnabled = false;

	/* Only over-written when Platform deserializes a legacy version of the state */
	private int deserializedVersion = CURRENT_VERSION;
	/* All of the state that is not itself hashed or serialized, but only derived from such state */
	private StateMetadata metadata;

	public ServicesState() {
		/* RuntimeConstructable */
	}

	private ServicesState(ServicesState that) {
		/* Copy the Merkle route from the source instance */
		super(that);
		/* Copy the non-null Merkle children from the source */
		for (int childIndex = 0, n = that.getNumberOfChildren(); childIndex < n; childIndex++) {
			final var childToCopy = that.getChild(childIndex);
			if (childToCopy != null) {
				setChild(childIndex, childToCopy.copy());
			}
		}
		/* Copy the non-Merkle state from the source */
		this.deserializedVersion = that.deserializedVersion;
		this.metadata = (that.metadata == null) ? null : that.metadata.copy();
	}

	/**
	 * Log out the sizes the state children.
	 */
	 private void logStateChildrenSizes() {
		log.info("  (@ {}) # NFTs               = {}",
				StateChildIndices.UNIQUE_TOKENS,
				uniqueTokens().size());
		log.info("  (@ {}) # token associations = {}",
				StateChildIndices.TOKEN_ASSOCIATIONS,
				tokenAssociations().size());
		log.info("  (@ {}) # topics             = {}",
				StateChildIndices.TOPICS,
				topics().size());
		log.info("  (@ {}) # blobs              = {}",
				StateChildIndices.STORAGE,
				storage().size());
		log.info("  (@ {}) # accounts/contracts = {}",
				StateChildIndices.ACCOUNTS,
				accounts().size());
		log.info("  (@ {}) # tokens             = {}",
				StateChildIndices.TOKENS,
				tokens().size());
		log.info("  (@ {}) # scheduled txns     = {}",
				StateChildIndices.SCHEDULE_TXS,
				scheduleTxs().size());
		log.info("  (@ {}) # contract K/V pairs = {}",
				StateChildIndices.CONTRACT_STORAGE,
				contractStorage().size());
	}

	/* --- MerkleInternal --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	@Override
	public int getMinimumChildCount(int version) {
		if (version >= MINIMUM_SUPPORTED_VERSION && version < CURRENT_VERSION) {
			return NUM_POST_0210_CHILDREN;
		} else if (version == CURRENT_VERSION) {
			return NUM_POST_0270_CHILDREN;
		}
		else {
			throw new IllegalArgumentException("Argument 'version='" + version + "' is invalid!");
		}
	}

	@Override
	public int getMinimumSupportedVersion() {
		return MINIMUM_SUPPORTED_VERSION;
	}

	@Override
	public void initialize() {
		// No new top-level children
	}

	@Override
	public void addDeserializedChildren(List<MerkleNode> children, int version) {
		super.addDeserializedChildren(children, version);
		deserializedVersion = version;
	}

	@Override
	public void migrate() {
		int deserializedVersionFromState = getDeserializedVersion();
		if (deserializedVersionFromState < RELEASE_025X_VERSION) {
			tokenRelsLinkMigrator.buildAccountTokenAssociationsLinkedList(accounts(), tokenAssociations());
			titleCountsMigrator.accept(this);
		}
		if (deserializedVersionFromState < RELEASE_0260_VERSION) {
			iterableStorageMigrator.makeStorageIterable(
					this,
					KvPairIterationMigrator::new,
					VirtualMapMigration::extractVirtualMapData,
					vmFactory.apply(JasperDbBuilder::new).newVirtualizedIterableStorage());
			ownedNftsLinkMigrator.buildAccountNftsOwnedLinkedList(accounts(), uniqueTokens());

			// When enabling expiry, we will grant all contracts a ~90 day auto-renewal via the autoRenewalMigrator
			if (expiryJustEnabled) {
				autoRenewalMigrator.grantFreeAutoRenew(this, getTimeOfLastHandledTxn());
			}

		}
		if (deserializedVersionFromState < RELEASE_0270_VERSION) {
			// build stakingInfo child
			setChild(StateChildIndices.STAKING_INFO, stakingInfoBuilder.buildStakingInfoMap(addressBook(), new BootstrapProperties()));
			// Give the MutableStateChildren up-to-date WeakReferences
			final var app = getMetadata().app();
			app.workingState().updatePrimitiveChildrenFrom(this);
		}
		log.info("Migration completed.");
		logStateChildrenSizes();
	}

	/* --- SwirldState --- */
	@Override
	public void init(final Platform platform, final AddressBook addressBook, final SwirldDualState dualState) {
		log.info("Init called on Services node {} WITH Merkle saved state", platform.getSelfId());

		/* Immediately override the address book from the saved state */
		setChild(StateChildIndices.ADDRESS_BOOK, addressBook);

		internalInit(platform, new BootstrapProperties(), dualState);
	}

	@Override
	public void genesisInit(Platform platform, AddressBook addressBook, final SwirldDualState dualState) {
		log.info("Init called on Services node {} WITHOUT Merkle saved state", platform.getSelfId());

		// Create the top-level children in the Merkle tree
		final var bootstrapProps = new BootstrapProperties();
		final var seqStart = bootstrapProps.getLongProperty("hedera.firstUserEntity");
		createGenesisChildren(addressBook, seqStart, bootstrapProps);

		internalInit(platform, bootstrapProps, dualState);
	}

	@Override
	public AddressBook getAddressBookCopy() {
		return addressBook().copy();
	}

	@Override
	public synchronized void handleTransaction(
			long submittingMember,
			boolean isConsensus,
			Instant creationTime,
			Instant consensusTime,
			SwirldTransaction transaction,
			SwirldDualState dualState
	) {
		if (isConsensus) {
			final var app = metadata.app();
			app.dualStateAccessor().setDualState(dualState);
			app.logic().incorporateConsensusTxn(transaction, consensusTime, submittingMember);
		}
	}

	@Override
	public void expandSignatures(final SwirldTransaction platformTxn) {
		try {
			final var app = metadata.app();
			final var accessor = app.expandHandleSpan().track(platformTxn);
			// Submit the transaction for any prepare stage processing that can be performed
			// such as pre-fetching of contract bytecode. This step is performed asynchronously
			// so get this step started before synchronous signature expansion.
			app.prefetchProcessor().submit(accessor);
			app.sigReqsManager().expandSigsInto(accessor);
		} catch (InvalidProtocolBufferException e) {
			log.warn("Method expandSignatures called with non-gRPC txn", e);
		} catch (Exception race) {
			log.warn("Unable to expand signatures, will be verified synchronously in handleTransaction", race);
		}
	}

	@Override
	public void noMoreTransactions() {
		// no-op
	}

	/* --- FastCopyable --- */
	@Override
	public synchronized ServicesState copy() {
		setImmutable(true);

		final var that = new ServicesState(this);
		if (metadata != null) {
			metadata.app().workingState().updateFrom(that);
		}

		return that;
	}

	/* --- Archivable --- */
	@Override
	public synchronized void archive() {
		if (metadata != null) {
			metadata.archive();
		}

		topics().archive();
		tokens().archive();
		accounts().archive();
		scheduleTxs().archive();
		uniqueTokens().archive();
		tokenAssociations().archive();
		stakingInfo().archive();
	}

	/* --- MerkleNode --- */
	@Override
	protected synchronized void onRelease() {
		if (metadata != null) {
			metadata.release();
		}
	}

	/* -- Getters and helpers -- */
	public AccountID getAccountFromNodeId(NodeId nodeId) {
		var address = addressBook().getAddress(nodeId.getId());
		var memo = address.getMemo();
		return parseAccount(memo);
	}

	public boolean isInitialized() {
		return metadata != null;
	}

	public Instant getTimeOfLastHandledTxn() {
		return networkCtx().consensusTimeOfLastHandledTxn();
	}

	public int getStateVersion() {
		return networkCtx().getStateVersion();
	}

	public void logSummary() {
		String ctxSummary;
		if (metadata != null) {
			final var app = metadata.app();
			app.hashLogger().logHashesFor(this);
			ctxSummary = networkCtx().summarizedWith(app.dualStateAccessor());
		} else {
			ctxSummary = networkCtx().summarized();
		}
		log.info(ctxSummary);
	}

	public Map<ByteString, EntityNum> aliases() {
		Objects.requireNonNull(metadata, "Cannot get aliases from an uninitialized state");
		return metadata.aliases();
	}

	public MerkleMap<EntityNum, MerkleAccount> accounts() {
		return getChild(StateChildIndices.ACCOUNTS);
	}

	public VirtualMap<VirtualBlobKey, VirtualBlobValue> storage() {
		return getChild(StateChildIndices.STORAGE);
	}

	public MerkleMap<EntityNum, MerkleTopic> topics() {
		return getChild(StateChildIndices.TOPICS);
	}

	public MerkleMap<EntityNum, MerkleToken> tokens() {
		return getChild(StateChildIndices.TOKENS);
	}

	public MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations() {
		return getChild(StateChildIndices.TOKEN_ASSOCIATIONS);
	}

	public MerkleMap<EntityNum, MerkleSchedule> scheduleTxs() {
		return getChild(StateChildIndices.SCHEDULE_TXS);
	}

	public MerkleNetworkContext networkCtx() {
		return getChild(StateChildIndices.NETWORK_CTX);
	}

	public AddressBook addressBook() {
		return getChild(StateChildIndices.ADDRESS_BOOK);
	}

	public MerkleSpecialFiles specialFiles() {
		return getChild(StateChildIndices.SPECIAL_FILES);
	}

	public RecordsRunningHashLeaf runningHashLeaf() {
		return getChild(StateChildIndices.RECORD_STREAM_RUNNING_HASH);
	}

	public MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens() {
		return getChild(StateChildIndices.UNIQUE_TOKENS);
	}

	public VirtualMap<ContractKey, IterableContractValue> contractStorage() {
		return getChild(StateChildIndices.CONTRACT_STORAGE);
	}

	public MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo() {
		return getChild(StateChildIndices.STAKING_INFO);
	}

	private void internalInit(
			final Platform platform,
			final BootstrapProperties bootstrapProps,
			SwirldDualState dualState
	) {
		final var selfId = platform.getSelfId().getId();

		ServicesApp app;
		if (APPS.includes(selfId)) {
			app = APPS.get(selfId);
		} else {
			final var nodeAddress = addressBook().getAddress(selfId);
			final var initialHash = runningHashLeaf().getRunningHash().getHash();
			app = appBuilder.get()
					.staticAccountMemo(nodeAddress.getMemo())
					.bootstrapProps(bootstrapProps)
					.initialHash(initialHash)
					.platform(platform)
					.selfId(selfId)
					.build();
			APPS.save(selfId, app);
		}

		if (dualState == null) {
			dualState = new DualStateImpl();
		}
		app.dualStateAccessor().setDualState(dualState);
		log.info("Dual state includes freeze time={} and last frozen={}",
				dualState.getFreezeTime(),
				dualState.getLastFrozenTime());

		final var stateVersion = networkCtx().getStateVersion();
		if (stateVersion > CURRENT_VERSION) {
			log.error("Fatal error, network state version {} > node software version {}",
					networkCtx().getStateVersion(),
					CURRENT_VERSION);
			app.systemExits().fail(1);
		} else {
			final var maybePostUpgrade = dualState.getFreezeTime() != null;
			if (maybePostUpgrade && dualState.getFreezeTime().equals(dualState.getLastFrozenTime())) {
				// This was an upgrade, discard now-obsolete preparation state
				networkCtx().discardPreparedUpgradeMeta();
				dualState.setFreezeTime(null);
			}
			if (stateVersion < CURRENT_VERSION) {
				// Only signal the MigrationRecordsManager to re-run if this is an upgrade
				networkCtx().markMigrationRecordsNotYetStreamed();
			}
			networkCtx().setStateVersion(CURRENT_VERSION);

			metadata = new StateMetadata(app, new FCHashMap<>());
			// Log state before migration.
			logStateChildrenSizes();
			// This updates the working state accessor with our children
			app.initializationFlow().runWith(this);

			// Ensure the prefetch queue is created and thread pool is active instead of waiting
			// for lazy-initialization to take place.
			app.prefetchProcessor();
			log.info("Created prefetch processor");

			logSummary();
			log.info("  --> Context initialized accordingly on Services node {}", selfId);
		}
	}

	int getDeserializedVersion() {
		return deserializedVersion;
	}

	void createGenesisChildren(AddressBook addressBook, long seqStart, BootstrapProperties bootstrapProperties) {
		final var virtualMapFactory = new VirtualMapFactory(JasperDbBuilder::new);

		setChild(StateChildIndices.UNIQUE_TOKENS, new MerkleMap<>());
		setChild(StateChildIndices.TOKEN_ASSOCIATIONS, new MerkleMap<>());
		setChild(StateChildIndices.TOPICS, new MerkleMap<>());
		setChild(StateChildIndices.STORAGE, virtualMapFactory.newVirtualizedBlobs());
		setChild(StateChildIndices.ACCOUNTS, new MerkleMap<>());
		setChild(StateChildIndices.TOKENS, new MerkleMap<>());
		setChild(StateChildIndices.NETWORK_CTX, genesisNetworkCtxWith(seqStart));
		setChild(StateChildIndices.SPECIAL_FILES, new MerkleSpecialFiles());
		setChild(StateChildIndices.SCHEDULE_TXS, new MerkleMap<>());
		setChild(StateChildIndices.RECORD_STREAM_RUNNING_HASH, genesisRunningHashLeaf());
		setChild(StateChildIndices.ADDRESS_BOOK, addressBook);
		setChild(StateChildIndices.CONTRACT_STORAGE, virtualMapFactory.newVirtualizedIterableStorage());
		setChild(StateChildIndices.STAKING_INFO, stakingInfoBuilder.buildStakingInfoMap(addressBook, bootstrapProperties));
	}

	private RecordsRunningHashLeaf genesisRunningHashLeaf() {
		final var genesisRunningHash = new RunningHash();
		genesisRunningHash.setHash(EMPTY_HASH);
		return new RecordsRunningHashLeaf(genesisRunningHash);
	}

	private MerkleNetworkContext genesisNetworkCtxWith(long seqStart) {
		return new MerkleNetworkContext(
				null,
				new SequenceNumber(seqStart),
				seqStart - 1,
				new ExchangeRates());
	}

	private static TokenRelsLinkMigrator tokenRelsLinkMigrator = ReleaseTwentyFiveMigration::buildAccountTokenAssociationsLinkedList;
	private static OwnedNftsLinkMigrator ownedNftsLinkMigrator = ReleaseTwentySixMigration::buildAccountNftsOwnedLinkedList;
	private static IterableStorageMigrator iterableStorageMigrator = ReleaseTwentySixMigration::makeStorageIterable;
	private static Consumer<ServicesState> titleCountsMigrator = ReleaseTwentyFiveMigration::initTreasuryTitleCounts;
	private static ContractAutoRenewalMigrator autoRenewalMigrator = ReleaseTwentySixMigration::grantFreeAutoRenew;
	private static StakingInfoBuilder stakingInfoBuilder = ReleaseTwentySevenMigration::buildStakingInfoMap;
	private static Function<JasperDbBuilderFactory, VirtualMapFactory> vmFactory = VirtualMapFactory::new;
	private static Supplier<ServicesApp.Builder> appBuilder = DaggerServicesApp::builder;

	@FunctionalInterface
	interface TokenRelsLinkMigrator {
		void buildAccountTokenAssociationsLinkedList(
				MerkleMap<EntityNum, MerkleAccount> accounts,
				MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations);
	}

	@FunctionalInterface
	interface OwnedNftsLinkMigrator {
		void buildAccountNftsOwnedLinkedList(
				MerkleMap<EntityNum, MerkleAccount> accounts,
				MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens
		);
	}

	@FunctionalInterface
	interface StakingInfoBuilder {
		MerkleMap<EntityNum, MerkleStakingInfo> buildStakingInfoMap(AddressBook addressBook, BootstrapProperties bootstrapProperties);
	}

	@FunctionalInterface
	interface ContractAutoRenewalMigrator {
		void grantFreeAutoRenew(ServicesState initializingState, Instant lastConsensusTime);
	}

	@FunctionalInterface
	interface IterableStorageMigrator {
		void makeStorageIterable(
				ServicesState initializingState,
				ReleaseTwentySixMigration.MigratorFactory migratorFactory,
				ReleaseTwentySixMigration.MigrationUtility migrationUtility,
				VirtualMap<ContractKey, IterableContractValue> iterableContractStorage);
	}

	@VisibleForTesting
	StateMetadata getMetadata() {
		return metadata;
	}

	@VisibleForTesting
	void setMetadata(final StateMetadata metadata) {
		this.metadata = metadata;
	}

	@VisibleForTesting
	void setDeserializedVersion(final int deserializedVersion) {
		this.deserializedVersion = deserializedVersion;
	}

	@VisibleForTesting
	static void setAppBuilder(final Supplier<ServicesApp.Builder> appBuilder) {
		ServicesState.appBuilder = appBuilder;
	}

	@VisibleForTesting
	static void setTokenRelsLinkMigrator(TokenRelsLinkMigrator tokenRelsLinkMigrator) {
		ServicesState.tokenRelsLinkMigrator = tokenRelsLinkMigrator;
	}

	@VisibleForTesting
	static void setOwnedNftsLinkMigrator(OwnedNftsLinkMigrator ownedNftsLinkMigrator) {
		ServicesState.ownedNftsLinkMigrator = ownedNftsLinkMigrator;
	}

	@VisibleForTesting
	static void setStakingInfoBuilder(StakingInfoBuilder stakingInfoBuilder) {
		ServicesState.stakingInfoBuilder = stakingInfoBuilder;
	}

	@VisibleForTesting
	static void setIterableStorageMigrator(final IterableStorageMigrator iterableStorageMigrator) {
		ServicesState.iterableStorageMigrator = iterableStorageMigrator;
	}

	@VisibleForTesting
	static void setTitleCountsMigrator(final Consumer<ServicesState> titleCountsMigrator) {
		ServicesState.titleCountsMigrator = titleCountsMigrator;
	}

	@VisibleForTesting
	static void setAutoRenewalMigrator(final ContractAutoRenewalMigrator autoRenewalMigrator) {
		ServicesState.autoRenewalMigrator = autoRenewalMigrator;
	}

	@VisibleForTesting
	static void setVmFactory(final Function<JasperDbBuilderFactory, VirtualMapFactory> vmFactory) {
		ServicesState.vmFactory = vmFactory;
	}

	@VisibleForTesting
	static void setExpiryJustEnabled(final boolean expiryJustEnabled) {
		ServicesState.expiryJustEnabled = expiryJustEnabled;
	}
}
