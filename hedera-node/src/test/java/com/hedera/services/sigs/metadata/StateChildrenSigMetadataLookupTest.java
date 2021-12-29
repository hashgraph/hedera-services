package com.hedera.services.sigs.metadata;

import com.hedera.services.config.MockFileNumbers;
import com.hedera.services.context.StateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.MetadataMapFactory;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.sigs.order.KeyOrderingFailure;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.sigs.order.KeyOrderingFailure.IMMUTABLE_CONTRACT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_CONTRACT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.INVALID_TOPIC;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_SCHEDULE;
import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_TOKEN;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StateChildrenSigMetadataLookupTest {
	@Mock
	private StateChildren stateChildren;
	@Mock
	private AliasManager aliasManager;
	@Mock
	private MerkleTopic topic;
	@Mock
	private MerkleToken token;
	@Mock
	private MerkleSchedule schedule;
	@Mock
	private MerkleAccount account;
	@Mock
	private TokenSigningMetadata tokenMeta;
	@Mock
	private MerkleMap<EntityNum, MerkleToken> tokens;
	@Mock
	private MerkleMap<EntityNum, MerkleTopic> topics;
	@Mock
	private Function<MerkleToken, TokenSigningMetadata> tokenMetaTransform;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private MerkleMap<EntityNum, MerkleSchedule> schedules;

	private MerkleMap<String, MerkleOptionalBlob> storage;
	private Map<FileID, HFileMeta> metaMap;

	private StateChildrenSigMetadataLookup subject;

	@BeforeEach
	void setUp() {
		subject = new StateChildrenSigMetadataLookup(
				new MockFileNumbers(), aliasManager, stateChildren, tokenMetaTransform);
	}

	@Test
	void recognizesMissingSchedule() {
		given(stateChildren.getSchedules()).willReturn(schedules);

		final var result = subject.scheduleSigningMetaFor(unknownSchedule);

		assertEquals(MISSING_SCHEDULE, result.failureIfAny());
	}

	@Test
	void recognizesScheduleWithoutExplicitPayer() {
		final var mockTxn = TransactionBody.newBuilder()
				.setContractCall(ContractCallTransactionBody.getDefaultInstance())
				.build();

		given(stateChildren.getSchedules()).willReturn(schedules);
		given(schedules.get(EntityNum.fromScheduleId(knownSchedule))).willReturn(schedule);
		given(schedule.adminKey()).willReturn(Optional.of(simple));
		given(schedule.ordinaryViewOfScheduledTxn()).willReturn(mockTxn);

		final var result = subject.scheduleSigningMetaFor(knownSchedule);

		assertTrue(result.succeeded());
		assertEquals(Optional.empty(), result.metadata().designatedPayer());
		assertSame(mockTxn, result.metadata().scheduledTxn());
		assertEquals(Optional.of(simple), result.metadata().adminKey());
	}

	@Test
	void recognizesScheduleWithExplicitPayer() {
		final var explicitPayer = new EntityId(0, 0, 5678);
		final var mockTxn = TransactionBody.newBuilder()
				.setContractCall(ContractCallTransactionBody.getDefaultInstance())
				.build();

		given(stateChildren.getSchedules()).willReturn(schedules);
		given(schedules.get(EntityNum.fromScheduleId(knownSchedule))).willReturn(schedule);
		given(schedule.hasExplicitPayer()).willReturn(true);
		given(schedule.payer()).willReturn(explicitPayer);
		given(schedule.adminKey()).willReturn(Optional.of(simple));
		given(schedule.ordinaryViewOfScheduledTxn()).willReturn(mockTxn);

		final var result = subject.scheduleSigningMetaFor(knownSchedule);

		assertTrue(result.succeeded());
		assertEquals(Optional.of(explicitPayer.toGrpcAccountId()), result.metadata().designatedPayer());
		assertSame(mockTxn, result.metadata().scheduledTxn());
		assertEquals(Optional.of(simple), result.metadata().adminKey());
	}

	@Test
	void recognizesMissingContract() {
		given(stateChildren.getAccounts()).willReturn(accounts);

		final var result = subject.contractSigningMetaFor(unknownContract);

		assertEquals(INVALID_CONTRACT, result.failureIfAny());
	}

	@Test
	void recognizesDeletedContract() {
		given(stateChildren.getAccounts()).willReturn(accounts);
		given(accounts.get(EntityNum.fromContractId(knownContract))).willReturn(account);
		given(account.isDeleted()).willReturn(true);

		final var result = subject.contractSigningMetaFor(knownContract);

		assertEquals(INVALID_CONTRACT, result.failureIfAny());
	}

	@Test
	void recognizesNonContractAccount() {
		given(stateChildren.getAccounts()).willReturn(accounts);
		given(accounts.get(EntityNum.fromContractId(knownContract))).willReturn(account);

		final var result = subject.contractSigningMetaFor(knownContract);

		assertEquals(INVALID_CONTRACT, result.failureIfAny());
	}

	@Test
	void recognizesImmutableContract() {
		given(stateChildren.getAccounts()).willReturn(accounts);
		given(accounts.get(EntityNum.fromContractId(knownContract))).willReturn(account);
		given(account.isSmartContract()).willReturn(true);

		final var nullResult = subject.contractSigningMetaFor(knownContract);
		assertEquals(IMMUTABLE_CONTRACT, nullResult.failureIfAny());

		given(account.getAccountKey()).willReturn(contract);
		final var contractResult = subject.contractSigningMetaFor(knownContract);
		assertEquals(IMMUTABLE_CONTRACT, contractResult.failureIfAny());
	}

	@Test
	void recognizesExtantContract() {
		given(stateChildren.getAccounts()).willReturn(accounts);
		given(accounts.get(EntityNum.fromContractId(knownContract))).willReturn(account);
		given(account.getAccountKey()).willReturn(simple);
		given(account.isSmartContract()).willReturn(true);
		given(account.isReceiverSigRequired()).willReturn(true);

		final var result = subject.contractSigningMetaFor(knownContract);

		assertTrue(result.succeeded());
		assertTrue(result.metadata().receiverSigRequired());
		assertSame(simple, result.metadata().key());
	}

	@Test
	void recognizesMissingAccountNum() {
		given(stateChildren.getAccounts()).willReturn(accounts);

		final var result = subject.accountSigningMetaFor(unknownAccount);

		assertEquals(MISSING_ACCOUNT, result.failureIfAny());
	}

	@Test
	void recognizesExtantAccount() {
		given(stateChildren.getAccounts()).willReturn(accounts);
		given(accounts.get(EntityNum.fromAccountId(knownAccount))).willReturn(account);
		given(account.getAccountKey()).willReturn(simple);
		given(account.isReceiverSigRequired()).willReturn(true);

		final var result = subject.aliasableAccountSigningMetaFor(knownAccount);

		assertTrue(result.succeeded());
		assertTrue(result.metadata().receiverSigRequired());
		assertSame(simple, result.metadata().key());
	}

	@Test
	void recognizesExtantAlias() {
		final var knownNum = EntityNum.fromAccountId(knownAccount);
		given(stateChildren.getAccounts()).willReturn(accounts);
		given(aliasManager.lookupIdBy(alias.getAlias())).willReturn(knownNum);
		given(accounts.get(knownNum)).willReturn(account);
		given(account.getAccountKey()).willReturn(simple);
		given(account.isReceiverSigRequired()).willReturn(true);

		final var result = subject.aliasableAccountSigningMetaFor(alias);

		assertTrue(result.succeeded());
		assertTrue(result.metadata().receiverSigRequired());
		assertSame(simple, result.metadata().key());
	}

	@Test
	void recognizesMissingAlias() {
		given(aliasManager.lookupIdBy(alias.getAlias())).willReturn(EntityNum.MISSING_NUM);

		final var result = subject.aliasableAccountSigningMetaFor(alias);

		assertEquals(MISSING_ACCOUNT, result.failureIfAny());
	}

	@Test
	void recognizesMissingToken() {
		given(stateChildren.getTokens()).willReturn(tokens);

		final var result = subject.tokenSigningMetaFor(unknownToken);

		assertEquals(MISSING_TOKEN, result.failureIfAny());
	}

	@Test
	void recognizesExtantToken() {
		given(stateChildren.getTokens()).willReturn(tokens);
		given(tokens.get(EntityNum.fromTokenId(knownToken))).willReturn(token);
		given(tokenMetaTransform.apply(token)).willReturn(tokenMeta);

		final var result = subject.tokenSigningMetaFor(knownToken);

		assertSame(tokenMeta, result.metadata());
	}

	@Test
	void includesTopicKeysIfPresent() {
		given(stateChildren.getTopics()).willReturn(topics);
		given(topics.get(EntityNum.fromTopicId(knownTopic))).willReturn(topic);
		given(topic.hasAdminKey()).willReturn(true);
		given(topic.hasSubmitKey()).willReturn(true);
		given(topic.getAdminKey()).willReturn(wacl);
		given(topic.getSubmitKey()).willReturn(simple);

		final var result = subject.topicSigningMetaFor(knownTopic);

		assertTrue(result.succeeded());
		assertSame(wacl, result.metadata().adminKey());
		assertSame(simple, result.metadata().submitKey());
	}

	@Test
	void omitsTopicKeysIfAbsent() {
		given(stateChildren.getTopics()).willReturn(topics);
		given(topics.get(EntityNum.fromTopicId(knownTopic))).willReturn(topic);

		final var result = subject.topicSigningMetaFor(knownTopic);

		assertTrue(result.succeeded());
		assertFalse(result.metadata().hasAdminKey());
		assertFalse(result.metadata().hasSubmitKey());
	}

	@Test
	void returnsMissingTopicMeta() {
		given(stateChildren.getTopics()).willReturn(topics);

		final var result = subject.topicSigningMetaFor(unknownTopic);

		assertEquals(INVALID_TOPIC, result.failureIfAny());
	}

	@Test
	void failsOnDeletedTopic() {
		given(stateChildren.getTopics()).willReturn(topics);
		given(topics.get(EntityNum.fromTopicId(knownTopic))).willReturn(topic);
		given(topic.isDeleted()).willReturn(true);

		final var result = subject.topicSigningMetaFor(knownTopic);

		Assertions.assertEquals(INVALID_TOPIC, result.failureIfAny());
	}

	@Test
	void returnsExtantFileMeta() {
		setupNonSpecialFileTest();
		givenFile(knownFile, false, expiry, wacl);

		final var result = subject.fileSigningMetaFor(knownFile);

		assertTrue(result.succeeded());
		assertEquals(wacl.toString(), result.metadata().wacl().toString());
	}

	@Test
	void failsOnMissingFileMeta() {
		setupNonSpecialFileTest();
		givenFile(knownFile, false, expiry, wacl);

		final var result = subject.fileSigningMetaFor(unknownFile);

		assertFalse(result.succeeded());
		assertEquals(KeyOrderingFailure.MISSING_FILE, result.failureIfAny());
	}

	@Test
	void returnsSpecialFileMeta() {
		final var result = subject.fileSigningMetaFor(knownSpecialFile);

		assertTrue(result.succeeded());
		assertSame(StateView.EMPTY_WACL, result.metadata().wacl());
	}

	private void setupNonSpecialFileTest() {
		storage = new MerkleMap<>();
		given(stateChildren.getStorage()).willReturn(storage);
		metaMap = MetadataMapFactory.metaMapFrom(new FcBlobsBytesStore(MerkleOptionalBlob::new, () -> storage));
	}

	private void givenFile(final FileID fid, final boolean isDeleted, final long expiry, final JKey wacl) {
		final var meta = new HFileMeta(isDeleted, wacl, expiry);
		metaMap.put(fid, meta);
	}

	private static final FileID knownFile = IdUtils.asFile("0.0.898989");
	private static final FileID unknownFile = IdUtils.asFile("0.0.989898");
	private static final FileID knownSpecialFile = IdUtils.asFile("0.0.150");
	private static final TopicID knownTopic = IdUtils.asTopic("0.0.1111");
	private static final TopicID unknownTopic = IdUtils.asTopic("0.0.2222");
	private static final TokenID knownToken = IdUtils.asToken("0.0.1111");
	private static final TokenID unknownToken = IdUtils.asToken("0.0.2222");
	private static final long expiry = 1_234_567L;
	private static final JKey simple = new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
	private static final JKeyList wacl = new JKeyList(List.of(
			new JECDSASecp256k1Key("012345789012345789012345789012".getBytes())
	));
	private static final JContractIDKey contract = new JContractIDKey(0, 0, 1234);
	private static final AccountID alias = AccountID.newBuilder()
			.setAlias(asKeyUnchecked(simple).toByteString())
			.build();
	private static final AccountID knownAccount = IdUtils.asAccount("0.0.1234");
	private static final AccountID unknownAccount = IdUtils.asAccount("0.0.4321");
	private static final ContractID knownContract = IdUtils.asContract("0.0.1234");
	private static final ContractID unknownContract = IdUtils.asContract("0.0.4321");
	private static final ScheduleID knownSchedule = IdUtils.asSchedule("0.0.1234");
	private static final ScheduleID unknownSchedule = IdUtils.asSchedule("0.0.4321");
}