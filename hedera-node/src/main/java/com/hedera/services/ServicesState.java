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
import com.hedera.services.context.init.InitializationFlow;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.context.properties.StandardizedPropertySources;
import com.hedera.services.sigs.HederaToPlatformSigOps;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.state.forensics.HashLogger;
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
import com.hedera.services.state.org.LegacyStateChildIndices;
import com.hedera.services.state.org.StateChildIndices;
import com.hedera.services.state.org.StateMetadata;
import com.hedera.services.state.org.StateVersions;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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
import com.swirlds.common.merkle.utility.AbstractNaryMerkleInternal;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.function.BiConsumer;

import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;
import static com.hedera.services.state.merkle.MerkleNetworkContext.UNKNOWN_CONSENSUS_TIME;
import static com.hedera.services.state.migration.Release0170Migration.moveLargeFcmsToBinaryRoutePositions;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;

/**
 * The Merkle tree root of the Hedera Services world state.
 */
public class ServicesState extends AbstractNaryMerkleInternal implements SwirldState.SwirldState2 {
	private static final Logger log = LogManager.getLogger(ServicesState.class);

	private static final long RUNTIME_CONSTRUCTABLE_ID = 0x8e300b0dfdafbb1aL;
	private static final ImmutableHash EMPTY_HASH = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

	private static HashLogger hashLogger = new HashLogger();
	private static ExpansionHelper expansionHelper = HederaToPlatformSigOps::expandIn;
	private static BiConsumer<ServicesState, ServicesContext> contextInitializer = InitializationFlow::accept;

	interface ExpansionHelper {
		ResponseCodeEnum expandIn(
				PlatformTxnAccessor txnAccessor,
				HederaSigningOrder keyOrderer,
				PubKeyToSigBytes pkToSigFn);
	}

	/* If positive, the version of the saved state loaded to create this instance; if -1, this is the genesis state */
	private int deserializedVersion = -1;
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
			throw new IllegalArgumentException("Argument 'version='" + version + "' is invalid!");
		}
	}

	@Override
	public int getMinimumSupportedVersion() {
		return StateVersions.MINIMUM_SUPPORTED_VERSION;
	}

	@Override
	public void initialize() {
		if (deserializedVersion == StateVersions.RELEASE_0160_VERSION) {
			moveLargeFcmsToBinaryRoutePositions(this);
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
			expansionHelper.expandIn(accessor, ctx.lookupRetryingKeyOrder(), accessor.getPkToSigsFn());
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
			metadata.getCtx().update(that);
		}

		return that;
	}

	/* --- Archivable --- */
	@Override
	public void archive() {
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
		hashLogger.logHashesFor(this);
		log.info(networkCtx());
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
		setImmutable(false);

		networkCtx().setStateVersion(StateVersions.CURRENT_VERSION);
		diskFs().checkHashesAgainstDiskContents();

		final var selfId = platform.getSelfId();
		ServicesContext ctx;
		if (CONTEXTS.isInitialized(selfId.getId())) {
			ctx = CONTEXTS.lookup(selfId.getId());
		} else {
			final var properties = new StandardizedPropertySources(bootstrapProps);
			ctx = new ServicesContext(selfId, platform, this, properties);
		}
		metadata = new StateMetadata(ctx);

		contextInitializer.accept(this, ctx);
		CONTEXTS.store(ctx);
		logSummary();

		log.info("  --> Context initialized accordingly on Services node {}", selfId);
	}

	private void createGenesisChildren(AddressBook addressBook, long seqStart) {
		setChild(StateChildIndices.UNIQUE_TOKENS, new FCMap<>());
		setChild(StateChildIndices.TOKEN_ASSOCIATIONS, new FCMap<>());
		setChild(StateChildIndices.TOPICS, new FCMap<>());
		setChild(StateChildIndices.STORAGE, new FCMap<>());
		setChild(StateChildIndices.ACCOUNTS, new FCMap<>());
		setChild(StateChildIndices.TOKENS, new FCMap<>());
		setChild(StateChildIndices.NETWORK_CTX, genesisNetworkCtxWith(seqStart));
		setChild(StateChildIndices.DISK_FS, new MerkleDiskFs());
		setChild(StateChildIndices.SCHEDULE_TXS, new FCMap<>());
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

	/* --- Only used by unit tests --- */
	static void setContextInitializer(BiConsumer<ServicesState, ServicesContext> contextInitializer) {
		ServicesState.contextInitializer = contextInitializer;
	}

	static void setHashLogger(HashLogger hashLogger) {
		ServicesState.hashLogger = hashLogger;
	}

	static void setExpansionHelper(ExpansionHelper expansionHelper) {
		ServicesState.expansionHelper = expansionHelper;
	}

	StateMetadata getMetadata() {
		return metadata;
	}

	void setMetadata(StateMetadata metadata) {
		this.metadata = metadata;
	}

	void setDeserializedVersion(int deserializedVersion) {
		this.deserializedVersion = deserializedVersion;
	}

	int getDeserializedVersion() {
		return deserializedVersion;
	}
}
