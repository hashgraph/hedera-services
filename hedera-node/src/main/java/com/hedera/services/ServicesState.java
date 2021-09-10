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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.migration.LegacyStateChildIndices;
import com.hedera.services.state.migration.StateChildIndices;
import com.hedera.services.state.migration.StateVersions;
import com.hedera.services.state.org.StateMetadata;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.hedera.services.store.tokens.views.internals.PermHashLong;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldDualState;
import com.swirlds.common.SwirldState;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.AbstractNaryMerkleInternal;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.FCMapMigration;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.context.AppsManager.APPS;
import static com.hedera.services.state.merkle.MerkleNetworkContext.UNKNOWN_CONSENSUS_TIME;
import static com.hedera.services.state.migration.Release0170Migration.moveLargeFcmsToBinaryRoutePositions;
import static com.hedera.services.store.tokens.views.internals.PermHashLong.fromLongs;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;

/**
 * The Merkle tree root of the Hedera Services world state.
 */
public class ServicesState extends AbstractNaryMerkleInternal implements SwirldState.SwirldState2 {
	private static final Logger log = LogManager.getLogger(ServicesState.class);

	private static final long RUNTIME_CONSTRUCTABLE_ID = 0x8e300b0dfdafbb1aL;
	private static final ImmutableHash EMPTY_HASH = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

	/* Only over-written when Platform deserializes a legacy version of the state */
	private int deserializedVersion = StateVersions.CURRENT_VERSION;
	/* All of the state that is not itself hashed or serialized, but only derived from such state */
	private StateMetadata metadata;

	/* Only needed for 0.18.0 to support migration from a 0.17.x state */
	private Platform platformForDeferredInit;
	private AddressBook addressBookForDeferredInit;

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

		this.platformForDeferredInit = that.platformForDeferredInit;
		this.addressBookForDeferredInit = that.addressBookForDeferredInit;
	}

	/* --- MerkleInternal --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return StateVersions.CURRENT_VERSION;
	}

	@Override
	public int getMinimumChildCount(int version) {
		if (version < StateVersions.RELEASE_0160_VERSION) {
			return StateChildIndices.NUM_PRE_0160_CHILDREN;
		} else if (version <= StateVersions.RELEASE_0180_VERSION) {
			return StateChildIndices.NUM_POST_0160_CHILDREN;
		} else {
			throw new IllegalArgumentException("Argument 'version='" + version + "' is invalid!");
		}
	}

	@Override
	public int getMinimumSupportedVersion() {
		return StateVersions.MINIMUM_SUPPORTED_VERSION;
	}

	@Override
	public void initialize() {
		if (deserializedVersion < StateVersions.RELEASE_0170_VERSION) {
			if (deserializedVersion < StateVersions.RELEASE_0160_VERSION) {
				setChild(LegacyStateChildIndices.UNIQUE_TOKENS, new MerkleMap<>());
			}
			moveLargeFcmsToBinaryRoutePositions(this, deserializedVersion);
		}
	}

	@Override
	public void addDeserializedChildren(List<MerkleNode> children, int version) {
		super.addDeserializedChildren(children, version);
		deserializedVersion = version;
	}

	@Override
	public void migrate() {
		if (getDeserializedVersion() < StateVersions.RELEASE_0180_VERSION) {
			log.info("Beginning FCMap -> MerkleMap migrations");
			fcmMigrator.toMerkleMap(
					this,
					StateChildIndices.UNIQUE_TOKENS,
					(MerkleUniqueTokenId uniqueTokenId) -> new PermHashLong(uniqueTokenId.identityCode()),
					(MerkleUniqueToken v) -> v);
			fcmMigrator.toMerkleMap(
					this,
					StateChildIndices.TOKEN_ASSOCIATIONS,
					(MerkleEntityAssociation tokenRel) -> fromLongs(tokenRel.getFromNum(), tokenRel.getToNum()),
					(MerkleTokenRelStatus v) -> v);
			fcmMigrator.toMerkleMap(
					this,
					StateChildIndices.TOPICS,
					(MerkleEntityId id) -> PermHashInteger.fromLong(id.getNum()),
					(MerkleTopic v) -> v);
			fcmMigrator.toMerkleMap(
					this,
					StateChildIndices.STORAGE,
					MerkleBlobMeta::getPath,
					(MerkleOptionalBlob v) -> v);
			fcmMigrator.toMerkleMap(
					this,
					StateChildIndices.ACCOUNTS,
					(MerkleEntityId id) -> PermHashInteger.fromLong(id.getNum()),
					(MerkleAccount v) -> v);
			fcmMigrator.toMerkleMap(
					this,
					StateChildIndices.TOKENS,
					(MerkleEntityId id) -> PermHashInteger.fromLong(id.getNum()),
					(MerkleToken v) -> v);
			fcmMigrator.toMerkleMap(
					this,
					StateChildIndices.SCHEDULE_TXS,
					(MerkleEntityId id) -> PermHashInteger.fromLong(id.getNum()),
					(MerkleSchedule v) -> v);
			log.info("Finished with FCMap -> MerkleMap migrations, completing the deferred init");

			init(getPlatformForDeferredInit(), getAddressBookForDeferredInit());
		}
	}

	/* --- SwirldState --- */
	@Override
	public void init(Platform platform, AddressBook addressBook) {
		if (deserializedVersion < StateVersions.RELEASE_0180_VERSION && platform != platformForDeferredInit) {
			/* Due to design issues with the BinaryObjectStore, which will not be finished
			initializing here, we need to defer initialization until post-FCM migration. */
			platformForDeferredInit = platform;
			addressBookForDeferredInit = addressBook;
			log.info("Deferring init for 0.17.x -> 0.18.x upgrade on Services node {}", platform.getSelfId());
			return;
		}

		log.info("Init called on Services node {} WITH Merkle saved state", platform.getSelfId());

		/* Immediately override the address book from the saved state */
		setChild(StateChildIndices.ADDRESS_BOOK, addressBook);

		internalInit(platform, new BootstrapProperties());
	}

	@Override
	public void genesisInit(Platform platform, AddressBook addressBook) {
		log.info("Init called on Services node {} WITHOUT Merkle saved state", platform.getSelfId());

		/* Create the top-level children in the Merkle tree */
		final var bootstrapProps = new BootstrapProperties();
		final var seqStart = bootstrapProps.getLongProperty("hedera.numReservedSystemEntities") + 1;
		createGenesisChildren(addressBook, seqStart);

		internalInit(platform, bootstrapProps);
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
	public void expandSignatures(SwirldTransaction platformTxn) {
		try {
			final var app = metadata.app();
			final var accessor = app.expandHandleSpan().track(platformTxn);
			app.expansionHelper().expandIn(accessor, app.retryingSigReqs(), accessor.getPkToSigsFn());
		} catch (InvalidProtocolBufferException e) {
			log.warn("Method expandSignatures called with non-gRPC txn", e);
		} catch (Exception race) {
			log.warn("Unable to expand signatures, will be verified synchronously in handleTransaction", race);
		}
	}

	@Override
	public void noMoreTransactions() {
		/* No-op. */
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
	public void archive() {
		/* NOTE: in the near future, likely SDK 0.19.0, it will be necessary
		 * to also propagate this .archive() call to the MerkleMaps as well. */
		if (metadata != null) {
			metadata.archive();
		}
	}

	/* --- MerkleNode --- */
	@Override
	protected void onRelease() {
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

	public void logSummary() {
		if (metadata != null) {
			metadata.app().hashLogger().logHashesFor(this);
		}
		log.info(networkCtx());
	}

	public MerkleMap<PermHashInteger, MerkleAccount> accounts() {
		return getChild(StateChildIndices.ACCOUNTS);
	}

	public MerkleMap<String, MerkleOptionalBlob> storage() {
		return getChild(StateChildIndices.STORAGE);
	}

	public MerkleMap<PermHashInteger, MerkleTopic> topics() {
		return getChild(StateChildIndices.TOPICS);
	}

	public MerkleMap<PermHashInteger, MerkleToken> tokens() {
		return getChild(StateChildIndices.TOKENS);
	}

	public MerkleMap<PermHashLong, MerkleTokenRelStatus> tokenAssociations() {
		return getChild(StateChildIndices.TOKEN_ASSOCIATIONS);
	}

	public MerkleMap<PermHashInteger, MerkleSchedule> scheduleTxs() {
		return getChild(StateChildIndices.SCHEDULE_TXS);
	}

	public MerkleNetworkContext networkCtx() {
		return getChild(StateChildIndices.NETWORK_CTX);
	}

	public AddressBook addressBook() {
		return getChild(StateChildIndices.ADDRESS_BOOK);
	}

	public MerkleDiskFs diskFs() {
		return getChild((StateChildIndices.DISK_FS));
	}

	public RecordsRunningHashLeaf runningHashLeaf() {
		return getChild(StateChildIndices.RECORD_STREAM_RUNNING_HASH);
	}

	public MerkleMap<PermHashLong, MerkleUniqueToken> uniqueTokens() {
		return getChild(StateChildIndices.UNIQUE_TOKENS);
	}

	public FCOneToManyRelation<PermHashInteger, Long> uniqueTokenAssociations() {
		return metadata.getUniqueTokenAssociations();
	}

	public FCOneToManyRelation<PermHashInteger, Long> uniqueOwnershipAssociations() {
		return metadata.getUniqueOwnershipAssociations();
	}

	public FCOneToManyRelation<PermHashInteger, Long> uniqueTreasuryOwnershipAssociations() {
		return metadata.getUniqueTreasuryOwnershipAssociations();
	}

	private void internalInit(Platform platform, BootstrapProperties bootstrapProps) {
		networkCtx().setStateVersion(StateVersions.CURRENT_VERSION);
		diskFs().checkHashesAgainstDiskContents();

		final var selfId = platform.getSelfId().getId();

		ServicesApp app;
		if (APPS.includes(selfId)) {
			app = APPS.get(selfId);
		} else {
			app = appBuilder.get()
					.bootstrapProps(bootstrapProps)
					.initialState(this)
					.platform(platform)
					.selfId(selfId)
					.build();
			APPS.save(selfId, app);
		}

		metadata = new StateMetadata(app);
		app.initializationFlow().runWith(this);

		logSummary();
		log.info("  --> Context initialized accordingly on Services node {}", selfId);
	}

	int getDeserializedVersion() {
		return deserializedVersion;
	}

	Platform getPlatformForDeferredInit() {
		return platformForDeferredInit;
	}

	AddressBook getAddressBookForDeferredInit() {
		return addressBookForDeferredInit;
	}

	void createGenesisChildren(AddressBook addressBook, long seqStart) {
		setChild(StateChildIndices.UNIQUE_TOKENS, new MerkleMap<>());
		setChild(StateChildIndices.TOKEN_ASSOCIATIONS, new MerkleMap<>());
		setChild(StateChildIndices.TOPICS, new MerkleMap<>());
		setChild(StateChildIndices.STORAGE, new MerkleMap<>());
		setChild(StateChildIndices.ACCOUNTS, new MerkleMap<>());
		setChild(StateChildIndices.TOKENS, new MerkleMap<>());
		setChild(StateChildIndices.NETWORK_CTX, genesisNetworkCtxWith(seqStart));
		setChild(StateChildIndices.DISK_FS, new MerkleDiskFs());
		setChild(StateChildIndices.SCHEDULE_TXS, new MerkleMap<>());
		setChild(StateChildIndices.RECORD_STREAM_RUNNING_HASH, genesisRunningHashLeaf());
		setChild(StateChildIndices.ADDRESS_BOOK, addressBook);
	}

	private RecordsRunningHashLeaf genesisRunningHashLeaf() {
		final var genesisRunningHash = new RunningHash();
		genesisRunningHash.setHash(EMPTY_HASH);
		return new RecordsRunningHashLeaf(genesisRunningHash);
	}

	private MerkleNetworkContext genesisNetworkCtxWith(long seqStart) {
		return new MerkleNetworkContext(
				UNKNOWN_CONSENSUS_TIME,
				new SequenceNumber(seqStart),
				seqStart - 1,
				new ExchangeRates());
	}

	@FunctionalInterface
	interface FcmMigrator {
		<OK extends MerkleNode, OV extends MerkleNode, K, V extends MerkleNode & Keyed<K>> void toMerkleMap(
				MerkleInternal parent,
				int mapIndex,
				Function<OK, K> keyConverter,
				Function<OV, V> valueConverter);
	}

	private static FcmMigrator fcmMigrator = FCMapMigration::FCMapToMerkleMap;
	private static Supplier<ServicesApp.Builder> appBuilder = DaggerServicesApp::builder;

	/* --- Only used by unit tests --- */
	StateMetadata getMetadata() {
		return metadata;
	}

	void setMetadata(StateMetadata metadata) {
		this.metadata = metadata;
	}

	void setDeserializedVersion(int deserializedVersion) {
		this.deserializedVersion = deserializedVersion;
	}

	static void setAppBuilder(Supplier<ServicesApp.Builder> appBuilder) {
		ServicesState.appBuilder = appBuilder;
	}

	static void setFcmMigrator(FcmMigrator fcmMigrator) {
		ServicesState.fcmMigrator = fcmMigrator;
	}
}
