package com.hedera.services.context.primitives;

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

import com.google.protobuf.ByteString;
import com.hedera.services.config.NetworkInfo;
import com.hedera.services.context.StateChildren;
import com.hedera.services.contracts.sources.AddressKeyedMapFactory;
import com.hedera.services.files.DataMapFactory;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.MetadataMapFactory;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.sigs.sourcing.KeyType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RawTokenRelationship;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.UniqTokenView;
import com.hedera.services.store.tokens.views.UniqTokenViewFactory;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusTopicInfo;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.hederahashgraph.api.proto.java.TokenPauseStatus;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.store.schedule.ScheduleStore.MISSING_SCHEDULE;
import static com.hedera.services.store.tokens.TokenStore.MISSING_TOKEN;
import static com.hedera.services.store.tokens.views.EmptyUniqTokenViewFactory.EMPTY_UNIQ_TOKEN_VIEW_FACTORY;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddressHex;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hedera.services.utils.EntityNum.fromContractId;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static java.util.Collections.unmodifiableMap;

public class StateView {
	private static final Logger log = LogManager.getLogger(StateView.class);

	/* EVM storage maps from 256-bit (32-byte) keys to 256-bit (32-byte) values */
	public static final long BYTES_PER_EVM_KEY_VALUE_PAIR = 64L;
	public static final AccountID WILDCARD_OWNER = AccountID.newBuilder().setAccountNum(0L).build();

	static BiFunction<StateView, EntityNum, List<TokenRelationship>> tokenRelsFn = StateView::tokenRels;

	static final byte[] EMPTY_BYTES = new byte[0];
	static final MerkleMap<?, ?> EMPTY_FCM = new MerkleMap<>();
	static final VirtualMap<?, ?> EMPTY_VM = new VirtualMap<>();
	static final FCOneToManyRelation<?, ?> EMPTY_FCOTMR = new FCOneToManyRelation<>();

	public static final JKey EMPTY_WACL = new JKeyList();
	public static final MerkleToken REMOVED_TOKEN = new MerkleToken(
			0L, 0L, 0, "", "",
			false, false, MISSING_ENTITY_ID);
	public static final StateView EMPTY_VIEW = new StateView(
			null, null, null, EMPTY_UNIQ_TOKEN_VIEW_FACTORY, null);

	private final TokenStore tokenStore;
	private final ScheduleStore scheduleStore;
	private final StateChildren stateChildren;
	private final UniqTokenView uniqTokenView;
	private final NetworkInfo networkInfo;

	Map<byte[], byte[]> contractBytecode;
	Map<FileID, byte[]> fileContents;
	Map<FileID, HFileMeta> fileAttrs;

	public StateView(
			@Nullable final TokenStore tokenStore,
			@Nullable final ScheduleStore scheduleStore,
			@Nullable final StateChildren stateChildren,
			final UniqTokenViewFactory uniqTokenViewFactory,
			final NetworkInfo networkInfo
	) {
		this.tokenStore = tokenStore;
		this.scheduleStore = scheduleStore;
		this.stateChildren = stateChildren;
		this.networkInfo = networkInfo;

		this.uniqTokenView = uniqTokenViewFactory.viewFor(
				tokenStore,
				this::tokens,
				this::uniqueTokens,
				this::nftsByType,
				this::nftsByOwner,
				this::treasuryNftsByType);

		final Map<String, byte[]> blobStore = unmodifiableMap(new FcBlobsBytesStore(this::storage));

		fileContents = DataMapFactory.dataMapFrom(blobStore);
		fileAttrs = MetadataMapFactory.metaMapFrom(blobStore);
		contractBytecode = AddressKeyedMapFactory.bytecodeMapFrom(blobStore);
	}

	public Optional<HFileMeta> attrOf(final FileID id) {
		return Optional.ofNullable(fileAttrs.get(id));
	}

	public Optional<byte[]> contentsOf(final FileID id) {
		if (stateChildren == null) {
			return Optional.empty();
		}
		final var specialFiles = stateChildren.specialFiles();
		if (specialFiles.contains(id)) {
			return Optional.ofNullable(specialFiles.get(id));
		} else {
			return Optional.ofNullable(fileContents.get(id));
		}
	}

	public Optional<byte[]> bytecodeOf(final ContractID id) {
		return Optional.ofNullable(contractBytecode.get(asSolidityAddress(id)));
	}

	public Optional<MerkleToken> tokenWith(final TokenID id) {
		return tokenStore == null || !tokenStore.exists(id)
				? Optional.empty()
				: Optional.of(tokenStore.get(id));
	}

	public Optional<TokenInfo> infoForToken(final TokenID tokenID) {
		if (tokenStore == null) {
			return Optional.empty();
		}
		try {
			var id = tokenStore.resolve(tokenID);
			if (id == MISSING_TOKEN) {
				return Optional.empty();
			}
			final var token = tokenStore.get(id);
			final var info = TokenInfo.newBuilder()
					.setLedgerId(networkInfo.ledgerId())
					.setTokenTypeValue(token.tokenType().ordinal())
					.setSupplyTypeValue(token.supplyType().ordinal())
					.setTokenId(id)
					.setDeleted(token.isDeleted())
					.setSymbol(token.symbol())
					.setName(token.name())
					.setMemo(token.memo())
					.setTreasury(token.treasury().toGrpcAccountId())
					.setTotalSupply(token.totalSupply())
					.setMaxSupply(token.maxSupply())
					.setDecimals(token.decimals())
					.setExpiry(Timestamp.newBuilder().setSeconds(token.expiry()));

			final var adminCandidate = token.adminKey();
			adminCandidate.ifPresent(k -> info.setAdminKey(asKeyUnchecked(k)));

			final var freezeCandidate = token.freezeKey();
			freezeCandidate.ifPresentOrElse(k -> {
				info.setDefaultFreezeStatus(tfsFor(token.accountsAreFrozenByDefault()));
				info.setFreezeKey(asKeyUnchecked(k));
			}, () -> info.setDefaultFreezeStatus(TokenFreezeStatus.FreezeNotApplicable));

			final var kycCandidate = token.kycKey();
			kycCandidate.ifPresentOrElse(k -> {
				info.setDefaultKycStatus(tksFor(token.accountsKycGrantedByDefault()));
				info.setKycKey(asKeyUnchecked(k));
			}, () -> info.setDefaultKycStatus(TokenKycStatus.KycNotApplicable));

			final var supplyCandidate = token.supplyKey();
			supplyCandidate.ifPresent(k -> info.setSupplyKey(asKeyUnchecked(k)));
			final var wipeCandidate = token.wipeKey();
			wipeCandidate.ifPresent(k -> info.setWipeKey(asKeyUnchecked(k)));
			final var feeScheduleCandidate = token.feeScheduleKey();
			feeScheduleCandidate.ifPresent(k -> info.setFeeScheduleKey(asKeyUnchecked(k)));

			final var pauseCandidate = token.pauseKey();
			pauseCandidate.ifPresentOrElse(k -> {
				info.setPauseKey(asKeyUnchecked(k));
				info.setPauseStatus(tokenPauseStatusOf(token.isPaused()));
			}, () -> info.setPauseStatus(TokenPauseStatus.PauseNotApplicable));

			if (token.hasAutoRenewAccount()) {
				info.setAutoRenewAccount(token.autoRenewAccount().toGrpcAccountId());
				info.setAutoRenewPeriod(Duration.newBuilder().setSeconds(token.autoRenewPeriod()));
			}

			info.addAllCustomFees(token.grpcFeeSchedule());

			return Optional.of(info.build());
		} catch (Exception unexpected) {
			log.warn(
					"Unexpected failure getting info for token {}!",
					readableId(tokenID),
					unexpected);
			return Optional.empty();
		}
	}

	public Optional<ConsensusTopicInfo> infoForTopic(final TopicID topicID) {

		final var merkleTopic = topics().get(EntityNum.fromTopicId(topicID));
		if (merkleTopic == null) {
			return Optional.empty();
		}

		final var info = ConsensusTopicInfo.newBuilder();
		if (merkleTopic.hasMemo()) {
			info.setMemo(merkleTopic.getMemo());
		}
		if (merkleTopic.hasAdminKey()) {
			info.setAdminKey(asKeyUnchecked(merkleTopic.getAdminKey()));
		}
		if (merkleTopic.hasSubmitKey()) {
			info.setSubmitKey(asKeyUnchecked(merkleTopic.getSubmitKey()));
		}
		info.setAutoRenewPeriod(Duration.newBuilder().setSeconds(merkleTopic.getAutoRenewDurationSeconds()));
		if (merkleTopic.hasAutoRenewAccountId()) {
			info.setAutoRenewAccount(asAccount(merkleTopic.getAutoRenewAccountId()));
		}
		info.setExpirationTime(merkleTopic.getExpirationTimestamp().toGrpc());
		info.setSequenceNumber(merkleTopic.getSequenceNumber());
		info.setRunningHash(ByteString.copyFrom(merkleTopic.getRunningHash()));
		info.setLedgerId(networkInfo.ledgerId());

		return Optional.of(info.build());
	}

	public Optional<ScheduleInfo> infoForSchedule(final ScheduleID scheduleID) {
		if (scheduleStore == null) {
			return Optional.empty();
		}
		try {
			final var id = scheduleStore.resolve(scheduleID);
			if (id == MISSING_SCHEDULE) {
				return Optional.empty();
			}
			final var schedule = scheduleStore.get(id);
			final var signatories = schedule.signatories();
			final var signatoriesList = KeyList.newBuilder();

			signatories.forEach(pubKey -> signatoriesList.addKeys(grpcKeyReprOf(pubKey)));

			final var info = ScheduleInfo.newBuilder()
					.setLedgerId(networkInfo.ledgerId())
					.setScheduleID(id)
					.setScheduledTransactionBody(schedule.scheduledTxn())
					.setScheduledTransactionID(schedule.scheduledTransactionId())
					.setCreatorAccountID(schedule.schedulingAccount().toGrpcAccountId())
					.setPayerAccountID(schedule.effectivePayer().toGrpcAccountId())
					.setSigners(signatoriesList)
					.setExpirationTime(Timestamp.newBuilder().setSeconds(schedule.expiry()));
			schedule.memo().ifPresent(info::setMemo);
			if (schedule.isDeleted()) {
				info.setDeletionTime(schedule.deletionTime());
			} else if (schedule.isExecuted()) {
				info.setExecutionTime(schedule.executionTime());
			}

			final var adminCandidate = schedule.adminKey();
			adminCandidate.ifPresent(k -> info.setAdminKey(asKeyUnchecked(k)));

			return Optional.of(info.build());
		} catch (Exception unexpected) {
			log.warn(
					"Unexpected failure getting info for schedule {}!",
					readableId(scheduleID),
					unexpected);
			return Optional.empty();
		}
	}

	private Key grpcKeyReprOf(final byte[] publicKey) {
		if (publicKey.length == KeyType.ECDSA_SECP256K1.getLength()) {
			return Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(publicKey)).build();
		} else {
			return Key.newBuilder().setEd25519(ByteString.copyFrom(publicKey)).build();
		}
	}

	public Optional<TokenNftInfo> infoForNft(final NftID target) {
		final var currentNfts = uniqueTokens();
		final var tokenId = EntityNum.fromTokenId(target.getTokenID());
		final var targetKey = EntityNumPair.fromLongs(tokenId.longValue(), target.getSerialNumber());
		if (!currentNfts.containsKey(targetKey)) {
			return Optional.empty();
		}
		final var targetNft = currentNfts.get(targetKey);
		var accountId = targetNft.getOwner().toGrpcAccountId();

		if (WILDCARD_OWNER.equals(accountId)) {
			var merkleToken = tokens().get(tokenId);
			if (merkleToken == null) {
				return Optional.empty();
			}
			accountId = merkleToken.treasury().toGrpcAccountId();
		}

		final var info = TokenNftInfo.newBuilder()
				.setLedgerId(networkInfo.ledgerId())
				.setNftID(target)
				.setAccountID(accountId)
				.setCreationTime(targetNft.getCreationTime().toGrpc())
				.setMetadata(ByteString.copyFrom(targetNft.getMetadata()))
				.build();
		return Optional.of(info);
	}

	public boolean nftExists(final NftID id) {
		final var tokenNum = EntityNum.fromTokenId(id.getTokenID());
		final var key = EntityNumPair.fromLongs(tokenNum.longValue(), id.getSerialNumber());
		return uniqueTokens().containsKey(key);
	}

	public Optional<TokenType> tokenType(final TokenID tokenID) {
		if (tokenStore == null) {
			return Optional.empty();
		}
		try {
			final var id = tokenStore.resolve(tokenID);
			if (id == MISSING_TOKEN) {
				return Optional.empty();
			}
			final var token = tokenStore.get(id);
			return Optional.ofNullable(TokenType.forNumber(token.tokenType().ordinal()));
		} catch (Exception unexpected) {
			log.warn(
					"Unexpected failure getting info for token {}!",
					readableId(tokenID),
					unexpected);
			return Optional.empty();
		}
	}

	public boolean tokenExists(final TokenID id) {
		return tokenStore != null && tokenStore.resolve(id) != MISSING_TOKEN;
	}

	public boolean scheduleExists(final ScheduleID id) {
		return scheduleStore != null && scheduleStore.resolve(id) != MISSING_SCHEDULE;
	}

	public Optional<FileGetInfoResponse.FileInfo> infoForFile(final FileID id) {
		try {
			return getFileInfo(id);
		} catch (NullPointerException e) {
			log.warn("View used without a properly initialized VirtualMap", e);
			return Optional.empty();
		}
	}

	private Optional<FileGetInfoResponse.FileInfo> getFileInfo(final FileID id) {
		final var attr = fileAttrs.get(id);
		if (attr == null) {
			return Optional.empty();
		}
		final var info = FileGetInfoResponse.FileInfo.newBuilder()
				.setLedgerId(networkInfo.ledgerId())
				.setFileID(id)
				.setMemo(attr.getMemo())
				.setDeleted(attr.isDeleted())
				.setExpirationTime(Timestamp.newBuilder().setSeconds(attr.getExpiry()))
				.setSize(Optional.ofNullable(fileContents.get(id)).orElse(EMPTY_BYTES).length);
		if (!attr.getWacl().isEmpty()) {
			info.setKeys(MiscUtils.asKeyUnchecked(attr.getWacl()).getKeyList());
		}
		return Optional.of(info.build());
	}

	public Optional<CryptoGetInfoResponse.AccountInfo> infoForAccount(final AccountID id, final AliasManager aliasManager) {
		final var accountEntityNum = id.getAlias().isEmpty() 
                      ? fromAccountId(id) 
                      : aliasManager.lookupIdBy(id.getAlias());
		final var account = accounts().get(accountEntityNum);
		if (account == null) {
			return Optional.empty();
		}

		final AccountID accountID = id.getAlias().isEmpty() ? id : accountEntityNum.toGrpcAccountId();
		final var info = CryptoGetInfoResponse.AccountInfo.newBuilder()
				.setLedgerId(networkInfo.ledgerId())
				.setKey(asKeyUnchecked(account.getAccountKey()))
				.setAccountID(accountID)
				.setAlias(account.getAlias())
				.setReceiverSigRequired(account.isReceiverSigRequired())
				.setDeleted(account.isDeleted())
				.setMemo(account.getMemo())
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(account.getAutoRenewSecs()))
				.setBalance(account.getBalance())
				.setExpirationTime(Timestamp.newBuilder().setSeconds(account.getExpiry()))
				.setContractAccountID(asSolidityAddressHex(accountID))
				.setOwnedNfts(account.getNftsOwned())
				.setMaxAutomaticTokenAssociations(account.getMaxAutomaticAssociations());
		Optional.ofNullable(account.getProxy())
				.map(EntityId::toGrpcAccountId)
				.ifPresent(info::setProxyAccountID);
		final var tokenRels = tokenRelsFn.apply(this, accountEntityNum);
		if (!tokenRels.isEmpty()) {
			info.addAllTokenRelationships(tokenRels);
		}
		return Optional.of(info.build());
	}

	public long numNftsOwnedBy(AccountID target) {
		final var account = accounts().get(fromAccountId(target));
		if (account == null) {
			return 0L;
		}
		return account.getNftsOwned();
	}

	public Optional<List<TokenNftInfo>> infoForAccountNfts(@Nonnull final AccountID aid, final long start,
			final long end) {
		final var account = accounts().get(fromAccountId(aid));
		if (account == null) {
			return Optional.empty();
		}
		final var answer = uniqTokenView.ownedAssociations(aid, start, end);
		final var infoWithLedgerId = addLedgerIdToTokenNftInfoList(answer);
		return Optional.of(infoWithLedgerId);
	}

	public Optional<List<TokenNftInfo>> infosForTokenNfts(@Nonnull final TokenID tid, final long start,
			final long end) {
		if (!tokenExists(tid)) {
			return Optional.empty();
		}
		final var answer = uniqTokenView.typedAssociations(tid, start, end);
		final var infoWithLedgerId = addLedgerIdToTokenNftInfoList(answer);
		return Optional.of(infoWithLedgerId);
	}

	public Optional<ContractGetInfoResponse.ContractInfo> infoForContract(final ContractID id) {
		final var contractId = fromContractId(id);
		final var contract = contracts().get(contractId);
		if (contract == null) {
			return Optional.empty();
		}

		var mirrorId = asAccount(id);
		final var storageSize = contract.getNumContractKvPairs() * BYTES_PER_EVM_KEY_VALUE_PAIR;
		final var info = ContractGetInfoResponse.ContractInfo.newBuilder()
				.setLedgerId(networkInfo.ledgerId())
				.setAccountID(mirrorId)
				.setDeleted(contract.isDeleted())
				.setContractID(id)
				.setMemo(contract.getMemo())
				.setStorage(storageSize)
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(contract.getAutoRenewSecs()))
				.setBalance(contract.getBalance())
				.setExpirationTime(Timestamp.newBuilder().setSeconds(contract.getExpiry()))
				.setContractAccountID(asSolidityAddressHex(mirrorId));
		final var tokenRels = tokenRelsFn.apply(this, contractId);
		if (!tokenRels.isEmpty()) {
			info.addAllTokenRelationships(tokenRels);
		}

		try {
			final var adminKey = JKey.mapJKey(contract.getAccountKey());
			info.setAdminKey(adminKey);
		} catch (Exception ignore) {
		}

		return Optional.of(info.build());
	}

	public MerkleMap<EntityNum, MerkleTopic> topics() {
		return stateChildren == null ? emptyMm() : stateChildren.topics();
	}

	public MerkleMap<EntityNum, MerkleAccount> accounts() {
		return stateChildren == null ? emptyMm() : stateChildren.accounts();
	}

	public MerkleMap<EntityNum, MerkleAccount> contracts() {
		return stateChildren == null ? emptyMm() : stateChildren.accounts();
	}

	public MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations() {
		return stateChildren == null ? emptyMm() : stateChildren.tokenAssociations();
	}

	public MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens() {
		return stateChildren == null ? emptyMm() : stateChildren.uniqueTokens();
	}

	public VirtualMap<VirtualBlobKey, VirtualBlobValue> storage() {
		return stateChildren == null ? emptyVm() : stateChildren.storage();
	}

	public VirtualMap<ContractKey, ContractValue> contractStorage() {
		return stateChildren == null ? emptyVm() : stateChildren.contractStorage();
	}

	MerkleMap<EntityNum, MerkleToken> tokens() {
		return stateChildren == null ? emptyMm() : stateChildren.tokens();
	}

	FCOneToManyRelation<EntityNum, Long> nftsByType() {
		return stateChildren == null ? emptyFcotmr() : stateChildren.uniqueTokenAssociations();
	}

	FCOneToManyRelation<EntityNum, Long> nftsByOwner() {
		return stateChildren == null ? emptyFcotmr() : stateChildren.uniqueOwnershipAssociations();
	}

	FCOneToManyRelation<EntityNum, Long> treasuryNftsByType() {
		return stateChildren == null ? emptyFcotmr() : stateChildren.uniqueOwnershipTreasuryAssociations();
	}

	UniqTokenView uniqTokenView() {
		return uniqTokenView;
	}

	private List<TokenNftInfo> addLedgerIdToTokenNftInfoList(final List<TokenNftInfo> tokenNftInfoList) {
		return tokenNftInfoList.stream()
				.map(info -> info.toBuilder().setLedgerId(networkInfo.ledgerId()).build())
				.toList();
	}

	private TokenFreezeStatus tfsFor(final boolean flag) {
		return flag ? TokenFreezeStatus.Frozen : TokenFreezeStatus.Unfrozen;
	}

	private TokenKycStatus tksFor(final boolean flag) {
		return flag ? TokenKycStatus.Granted : TokenKycStatus.Revoked;
	}

	private TokenPauseStatus tokenPauseStatusOf(final boolean flag) {
		return flag ? TokenPauseStatus.Paused : TokenPauseStatus.Unpaused;
	}

	static List<TokenRelationship> tokenRels(final StateView view, final EntityNum id) {
		final var account = view.accounts().get(id);
		final List<TokenRelationship> relationships = new ArrayList<>();
		final var tokenIds = account.tokens().asTokenIds();
		for (TokenID tId : tokenIds) {
			final var optionalToken = view.tokenWith(tId);
			final var effectiveToken = optionalToken.orElse(REMOVED_TOKEN);
			final var relKey = fromAccountTokenRel(id.toGrpcAccountId(), tId);
			final var relationship = view.tokenAssociations().get(relKey);
			relationships.add(new RawTokenRelationship(
					relationship.getBalance(),
					tId.getShardNum(),
					tId.getRealmNum(),
					tId.getTokenNum(),
					relationship.isFrozen(),
					relationship.isKycGranted(),
					relationship.isAutomaticAssociation()
			).asGrpcFor(effectiveToken));
		}
		return relationships;
	}

	private static <K, V extends MerkleNode & Keyed<K>> MerkleMap<K, V> emptyMm() {
		return (MerkleMap<K, V>) EMPTY_FCM;
	}

	private static <K extends VirtualKey<K>, V extends VirtualValue> VirtualMap<K, V> emptyVm() {
		return (VirtualMap<K, V>) EMPTY_VM;
	}

	private static <K, V> FCOneToManyRelation<K, V> emptyFcotmr() {
		return (FCOneToManyRelation<K, V>) EMPTY_FCOTMR;
	}
}
