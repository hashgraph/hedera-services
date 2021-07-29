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
import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.context.properties.StandardizedPropertySources;
import com.hedera.services.exceptions.ContextNotFoundException;
import com.hedera.services.state.LegacyStateChildIndices;
import com.hedera.services.state.StateChildIndices;
import com.hedera.services.state.StateMetadata;
import com.hedera.services.state.StateVersions;
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
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.blob.BinaryObjectStore;
import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldDualState;
import com.swirlds.common.SwirldState;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.copy.MerkleCopy;
import com.swirlds.common.merkle.utility.AbstractNaryMerkleInternal;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;
import static com.hedera.services.sigs.HederaToPlatformSigOps.expandIn;
import static com.hedera.services.state.merkle.MerkleNetworkContext.UNKNOWN_CONSENSUS_TIME;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;

public class ServicesState extends AbstractNaryMerkleInternal implements SwirldState.SwirldState2 {
	private static final Logger log = LogManager.getLogger(ServicesState.class);

	private static final long RUNTIME_CONSTRUCTABLE_ID = 0x8e300b0dfdafbb1aL;
	private static final String UNSUPPORTED_VERSION_MSG_TPL = "Argument 'version=%d' is invalid!";
	private static final ImmutableHash EMPTY_HASH = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

	static Supplier<BinaryObjectStore> blobStoreSupplier = BinaryObjectStore::getInstance;

	private int deserializedVersion = -1;

	/* All of the state that is not itself hashed or serialized,
	but only derived from the hashed and serialized state. */
	private StateMetadata metadata;

	public ServicesState() {
		/* RuntimeConstructable */
	}

	private ServicesState(ServicesState that) {
		/* Copies the Merkle route from this instance to the new one */
		super(that);

		/* Copy all non-null Merkle children from the source state */
		for (int childIndex = 0, n = getNumberOfChildren(); childIndex < n; childIndex++) {
			final var childToCopy = that.getChild(childIndex);
			if (childToCopy != null) {
				setChild(childIndex, childToCopy.copy());
			}
		}

		this.metadata = (that.metadata == null) ? null : that.metadata.copy();
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
		if (version == StateVersions.RELEASE_0160_VERSION) {
			return LegacyStateChildIndices.NUM_0160_CHILDREN;
		} else if (version == StateVersions.RELEASE_0170_VERSION ) {
			return StateChildIndices.NUM_0170_CHILDREN;
		} else {
			throw new IllegalArgumentException(String.format(UNSUPPORTED_VERSION_MSG_TPL, version));
		}
	}

	@Override
	public int getMinimumSupportedVersion() {
		return StateVersions.MINIMUM_SUPPORTED_VERSION;
	}

	@Override
	public void genesisInit(Platform platform, AddressBook addressBook) {
		log.info("Init called on Services node {} WITHOUT Merkle saved state", platform.getSelfId());

		var bootstrapProps = new BootstrapProperties();
		long seqStart = bootstrapProps.getLongProperty("hedera.numReservedSystemEntities") + 1;
		setChild(StateChildIndices.NETWORK_CTX, new MerkleNetworkContext(
				UNKNOWN_CONSENSUS_TIME,
				new SequenceNumber(seqStart),
				seqStart - 1,
				new ExchangeRates()));
		setChild(StateChildIndices.TOPICS, new FCMap<>());
		setChild(StateChildIndices.STORAGE, new FCMap<>());
		setChild(StateChildIndices.ACCOUNTS, new FCMap<>());
		setChild(StateChildIndices.TOKENS, new FCMap<>());
		setChild(StateChildIndices.TOKEN_ASSOCIATIONS, new FCMap<>());
		setChild(StateChildIndices.DISK_FS, new MerkleDiskFs());
		setChild(StateChildIndices.SCHEDULE_TXS, new FCMap<>());
		setChild(StateChildIndices.UNIQUE_TOKENS, new FCMap<MerkleUniqueTokenId, MerkleUniqueToken>());
		final var firstRunningHash = new RunningHash();
		firstRunningHash.setHash(EMPTY_HASH);
		setChild(StateChildIndices.RECORD_STREAM_RUNNING_HASH, new RecordsRunningHashLeaf(firstRunningHash));

		internalInit(platform, addressBook, bootstrapProps);
	}

	@Override
	public void initialize() {
		if (deserializedVersion != StateVersions.CURRENT_VERSION) {
			/* First swap the address book and unique tokens */
			final var mutableAddressBook = getChild(LegacyStateChildIndices.ADDRESS_BOOK).copy();
			MerkleCopy.copyTreeToLocation(
					this, StateChildIndices.UNIQUE_TOKENS, getChild(LegacyStateChildIndices.UNIQUE_TOKENS));
			setChild(StateChildIndices.ADDRESS_BOOK, mutableAddressBook);

			/* Second swap the network context and tokens */
			final var mutableNetworkContext = networkCtx().copy();
			MerkleCopy.copyTreeToLocation(
					this, StateChildIndices.TOKENS, getChild(LegacyStateChildIndices.TOKENS));
			setChild(StateChildIndices.NETWORK_CTX, mutableNetworkContext);
		}
	}

	@Override
	public void addDeserializedChildren(List<MerkleNode> children, int version) {
		super.addDeserializedChildren(children, version);
		deserializedVersion = version;
	}

	/* --- SwirldState --- */
	@Override
	public void init(Platform platform, AddressBook addressBook) {
		log.info("Init called on Services node {} WITH Merkle saved state", platform.getSelfId());
		internalInit(platform, addressBook, new BootstrapProperties());
	}

	private void internalInit(Platform platform, AddressBook addressBook, BootstrapProperties bootstrapProps) {
		setImmutable(false);

		/* Note this overrides the address book from the saved state if it is present. */
		setChild(StateChildIndices.ADDRESS_BOOK, addressBook);
		networkCtx().setStateVersion(StateVersions.CURRENT_VERSION);

		final var selfId = platform.getSelfId();
		ServicesContext ctx;
		try {
			ctx = CONTEXTS.lookup(selfId.getId());
		} catch (ContextNotFoundException ignoreToInstantiateNewContext) {
			final var properties = new StandardizedPropertySources(bootstrapProps);
			ctx = new ServicesContext(selfId, platform, this, properties);
		}

		metadata = new StateMetadata(ctx, selfId);
		initializeContext(ctx);
		CONTEXTS.store(ctx);

		logSummary();
		log.info("  --> Context initialized accordingly on Services node {}", selfId);
	}

	private void initializeContext(final ServicesContext ctx) {
		ctx.setRecordsInitialHash(runningHashLeaf().getRunningHash().getHash());
		/* Set the primitive state in the context and signal the managing stores (if
		 * they are already constructed) to rebuild their auxiliary views of the state.
		 * All the initialization that follows will be a function of the primitive state. */
		ctx.update(this);
		ctx.rebuildBackingStoresIfPresent();
		ctx.rebuildStoreViewsIfPresent();
		ctx.uniqTokenViewsManager().rebuildNotice(tokens(), uniqueTokens());
		/* Use any payer records stored in state to rebuild the recent transaction
		 * history. This history has two main uses: Purging expired records, and
		 * classifying duplicate transactions. */
		ctx.recordsHistorian().reviewExistingRecords();
		/* Use any entities stored in state to rebuild queue of expired entities. */
		ctx.expiries().reviewExistingShortLivedEntities();
		/* Re-initialize the "observable" system files; that is, the files which have
	 	associated callbacks managed by the SysFilesCallback object. We explicitly
	 	re-mark the files are not loaded here, in case this is a reconnect. (During a
	 	reconnect the blob store might still be reloading, and we will finish loading
	 	the observable files in the ServicesMain.init method.) */
		ctx.networkCtxManager().setObservableFilesNotLoaded();
		if (!blobStoreSupplier.get().isInitializing()) {
			ctx.networkCtxManager().loadObservableSysFilesIfNeeded();
		}
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
			final var ctx = metadata.getCtx();
			ctx.setDualState(dualState);
			ctx.logic().incorporateConsensusTxn(transaction, consensusTime, submittingMember);
		}
	}

	@Override
	public void expandSignatures(SwirldTransaction platformTxn) {
		try {
			final var ctx = metadata.getCtx();
			final var accessor = ctx.expandHandleSpan().track(platformTxn);
			expandIn(accessor, ctx.lookupRetryingKeyOrder(), accessor.getPkToSigsFn());
		} catch (InvalidProtocolBufferException e) {
			log.warn("expandSignatures called with non-gRPC txn!", e);
		} catch (Exception race) {
			log.warn("Unexpected problem, signatures will be verified synchronously in handleTransaction!", race);
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
			metadata.getCtx().update(that);
		}

		return that;
	}

	/* --- Archivable --- */
	@Override
	public void archive() {
		metadata.archive();
	}

	/* --- MerkleNode --- */
	@Override
	protected void onRelease() {
		metadata.release();
	}

	/* --------------- */
	public AccountID getAccountFromNodeId(NodeId nodeId) {
		var address = addressBook().getAddress(nodeId.getId());
		var memo = address.getMemo();
		return parseAccount(memo);
	}

	public void logSummary() {
		logHashes();
		log.info(networkCtx().toString());
	}

	private void logHashes() {
		log.info(String.format("[SwirldState Hashes]\n" +
						"  Overall                :: %s\n" +
						"  Accounts               :: %s\n" +
						"  Storage                :: %s\n" +
						"  Topics                 :: %s\n" +
						"  Tokens                 :: %s\n" +
						"  TokenAssociations      :: %s\n" +
						"  DiskFs                 :: %s\n" +
						"  ScheduledTxs           :: %s\n" +
						"  NetworkContext         :: %s\n" +
						"  AddressBook            :: %s\n" +
						"  RecordsRunningHashLeaf :: %s\n" +
						"    ↪ Running hash       :: %s\n" +
						"  UniqueTokens           :: %s\n",
				getHash(),
				accounts().getHash(),
				storage().getHash(),
				topics().getHash(),
				tokens().getHash(),
				tokenAssociations().getHash(),
				diskFs().getHash(),
				scheduleTxs().getHash(),
				networkCtx().getHash(),
				addressBook().getHash(),
				runningHashLeaf().getHash(),
				runningHashLeaf().getRunningHash().getHash(),
				uniqueTokens().getHash()));
	}

	public FCMap<MerkleEntityId, MerkleAccount> accounts() {
		return getChild(StateChildIndices.ACCOUNTS);
	}

	public FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage() {
		return getChild(StateChildIndices.STORAGE);
	}

	public FCMap<MerkleEntityId, MerkleTopic> topics() {
		return getChild(StateChildIndices.TOPICS);
	}

	public FCMap<MerkleEntityId, MerkleToken> tokens() {
		return getChild(StateChildIndices.TOKENS);
	}

	public FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations() {
		return getChild(StateChildIndices.TOKEN_ASSOCIATIONS);
	}

	public FCMap<MerkleEntityId, MerkleSchedule> scheduleTxs() {
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

	public FCMap<MerkleUniqueTokenId, MerkleUniqueToken> uniqueTokens() {
		return getChild(StateChildIndices.UNIQUE_TOKENS);
	}

	public FCOneToManyRelation<Integer, Long> uniqueTokenAssociations() {
		return metadata.getUniqueTokenAssociations();
	}

	public FCOneToManyRelation<Integer, Long> uniqueOwnershipAssociations() {
		return metadata.getUniqueOwnershipAssociations();
	}

	public FCOneToManyRelation<Integer, Long> uniqueTreasuryOwnershipAssociations() {
		return metadata.getUniqueTreasuryOwnershipAssociations();
	}
}
