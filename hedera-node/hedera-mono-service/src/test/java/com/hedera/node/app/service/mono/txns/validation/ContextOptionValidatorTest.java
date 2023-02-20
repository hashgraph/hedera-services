/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.node.app.service.mono.txns.validation;

import static com.hedera.node.app.service.mono.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromContractId;
import static com.hedera.node.app.spi.config.PropertyNames.ENTITIES_MAX_LIFETIME;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asFile;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_QUERY_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.METADATA_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.factories.topics.TopicFactory;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContextOptionValidatorTest {

    private final Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
    private final Instant now = Instant.now();
    private final AccountID a = AccountID.newBuilder().setAccountNum(9_999L).build();
    private final MerkleAccount aV = MerkleAccountFactory.newAccount().get();
    private final AccountID b = AccountID.newBuilder().setAccountNum(8_999L).build();
    private final AccountID d = AccountID.newBuilder().setAccountNum(6_999L).build();
    private final AccountID missing =
            AccountID.newBuilder().setAccountNum(1_234L).build();
    private final AccountID thisNodeAccount =
            AccountID.newBuilder().setAccountNum(13L).build();
    private final ContractID missingContract =
            ContractID.newBuilder().setContractNum(5_431L).build();
    private final AccountID deleted =
            AccountID.newBuilder().setAccountNum(2_234L).build();
    private final MerkleAccount deletedV =
            MerkleAccountFactory.newAccount().deleted(true).get();
    private final ContractID contract =
            ContractID.newBuilder().setContractNum(5_432L).build();
    private final MerkleAccount contractV =
            MerkleAccountFactory.newAccount().isSmartContract(true).get();
    private final ContractID deletedContract =
            ContractID.newBuilder().setContractNum(4_432L).build();
    private final MerkleAccount deletedContractV = MerkleAccountFactory.newAccount()
            .isSmartContract(true)
            .deleted(true)
            .get();
    private final TopicID missingTopicId =
            TopicID.newBuilder().setTopicNum(1_234L).build();
    private final TopicID deletedTopicId =
            TopicID.newBuilder().setTopicNum(2_345L).build();
    private final TopicID expiredTopicId =
            TopicID.newBuilder().setTopicNum(3_456L).build();
    private final TopicID topicId = TopicID.newBuilder().setTopicNum(4_567L).build();
    private final ByteString ledgerId = ByteString.copyFromUtf8("0xff");
    PropertySource properties;
    GlobalDynamicProperties dynamicProperties;
    private MerkleTopic deletedMerkleTopic;
    private MerkleTopic expiredMerkleTopic;
    private MerkleTopic merkleTopic;
    private MerkleMap topics;
    private AccountStorageAdapter accounts;
    private TransactionContext txnCtx;
    private ContextOptionValidator subject;
    private JKey wacl;
    private HFileMeta attr;
    private HFileMeta deletedAttr;
    private StateView view;
    private final long expiry = 2_000_000L;
    private final long maxLifetime = 3_000_000L;
    private final FileID target = asFile("0.0.123");

    @BeforeEach
    void setup() throws Exception {
        txnCtx = mock(TransactionContext.class);
        given(txnCtx.consensusTime()).willReturn(now);
        accounts = mock(AccountStorageAdapter.class);
        given(accounts.get(EntityNum.fromAccountId(a))).willReturn(aV);
        given(accounts.get(EntityNum.fromAccountId(deleted))).willReturn(deletedV);
        given(accounts.get(fromContractId(contract))).willReturn(contractV);
        given(accounts.get(fromContractId(deletedContract))).willReturn(deletedContractV);

        dynamicProperties = mock(GlobalDynamicProperties.class);
        given(dynamicProperties.maxMemoUtf8Bytes()).willReturn(100);
        properties = mock(PropertySource.class);
        given(properties.getLongProperty(ENTITIES_MAX_LIFETIME)).willReturn(maxLifetime);

        topics = mock(MerkleMap.class);
        deletedMerkleTopic = TopicFactory.newTopic().deleted(true).get();
        expiredMerkleTopic = TopicFactory.newTopic()
                .expiry(now.minusSeconds(555L).getEpochSecond())
                .get();
        merkleTopic = TopicFactory.newTopic()
                .memo("Hi, over here!")
                .expiry(now.plusSeconds(555L).getEpochSecond())
                .get();
        given(topics.get(EntityNum.fromTopicId(topicId))).willReturn(merkleTopic);
        given(topics.get(EntityNum.fromTopicId(missingTopicId))).willReturn(null);
        given(topics.get(EntityNum.fromTopicId(deletedTopicId))).willReturn(deletedMerkleTopic);
        given(topics.get(EntityNum.fromTopicId(expiredTopicId))).willReturn(expiredMerkleTopic);

        final NodeInfo nodeInfo = mock(NodeInfo.class);
        given(nodeInfo.selfAccount()).willReturn(thisNodeAccount);

        wacl = TxnHandlingScenario.SIMPLE_NEW_WACL_KT.asJKey();
        attr = new HFileMeta(false, wacl, expiry);
        deletedAttr = new HFileMeta(true, wacl, expiry);
        view = mock(StateView.class);

        subject = new ContextOptionValidator(nodeInfo, properties, txnCtx, dynamicProperties);
    }

    private FileGetInfoResponse.FileInfo asMinimalInfo(final HFileMeta meta) throws Exception {
        return FileGetInfoResponse.FileInfo.newBuilder()
                .setLedgerId(ledgerId)
                .setDeleted(meta.isDeleted())
                .setKeys(JKey.mapJKey(meta.getWacl()).getKeyList())
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shortCircuitsLedgerExpiryCheckIfNoExpiryEnabled() {
        final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts = mock(TransactionalLedger.class);
        given(dynamicProperties.shouldAutoRenewSomeEntityType()).willReturn(false);
        assertEquals(OK, subject.expiryStatusGiven(accounts, thisNodeAccount));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shortCircuitsLedgerExpiryCheckIfBalanceIsNonZero() {
        final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts = mock(TransactionalLedger.class);
        given(dynamicProperties.shouldAutoRenewSomeEntityType()).willReturn(true);
        given(accounts.get(thisNodeAccount, AccountProperty.BALANCE)).willReturn(1L);
        assertEquals(OK, subject.expiryStatusGiven(accounts, thisNodeAccount));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shortCircuitsIfBalanceIsZeroButNotDetached() {
        final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts = mock(TransactionalLedger.class);
        given(dynamicProperties.shouldAutoRenewSomeEntityType()).willReturn(true);
        given(dynamicProperties.shouldAutoRenewContracts()).willReturn(true);
        given(accounts.get(thisNodeAccount, AccountProperty.BALANCE)).willReturn(0L);
        given(accounts.get(thisNodeAccount, AccountProperty.EXPIRED_AND_PENDING_REMOVAL))
                .willReturn(false);
        assertEquals(OK, subject.expiryStatusGiven(accounts, thisNodeAccount));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shortCircuitsIfContractExpiryNotEnabled() {
        final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts = mock(TransactionalLedger.class);
        given(dynamicProperties.shouldAutoRenewSomeEntityType()).willReturn(true);
        given(accounts.get(thisNodeAccount, AccountProperty.BALANCE)).willReturn(0L);
        given(accounts.get(thisNodeAccount, AccountProperty.EXPIRED_AND_PENDING_REMOVAL))
                .willReturn(true);
        given(accounts.get(thisNodeAccount, AccountProperty.IS_SMART_CONTRACT)).willReturn(true);
        assertEquals(OK, subject.expiryStatusGiven(accounts, thisNodeAccount));
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesPreciseExpiryCheckIfBalanceIsZero() {
        final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts = mock(TransactionalLedger.class);
        given(dynamicProperties.shouldAutoRenewSomeEntityType()).willReturn(true);
        given(dynamicProperties.shouldAutoRenewContracts()).willReturn(true);
        given(accounts.get(thisNodeAccount, AccountProperty.BALANCE)).willReturn(0L);
        given(accounts.get(thisNodeAccount, AccountProperty.EXPIRED_AND_PENDING_REMOVAL))
                .willReturn(true);
        given(accounts.get(thisNodeAccount, AccountProperty.IS_SMART_CONTRACT)).willReturn(true);
        assertEquals(CONTRACT_EXPIRED_AND_PENDING_REMOVAL, subject.expiryStatusGiven(accounts, thisNodeAccount));
    }

    @Test
    void alwaysOkExpiryStatusIfNonzeroBalance() {
        final var status = subject.expiryStatusGiven(1L, true, true);
        assertEquals(OK, status);
    }

    @Test
    void alwaysOkIfNotDetached() {
        final var status = subject.expiryStatusGiven(0L, false, true);
        assertEquals(OK, status);
    }

    @Test
    void contractIsExpiredIfZeroBalanceAndPastExpiry() {
        given(dynamicProperties.shouldAutoRenewContracts()).willReturn(true);
        final var status = subject.expiryStatusGiven(0, true, true);
        assertEquals(CONTRACT_EXPIRED_AND_PENDING_REMOVAL, status);
    }

    @Test
    void accountIsExpiredIfZeroBalanceAndDetached() {
        given(dynamicProperties.shouldAutoRenewAccounts()).willReturn(true);
        final var status = subject.expiryStatusGiven(0, true, false);
        assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, status);
    }

    @Test
    void ifAccountExpiryNotEnabledItsOk() {
        final var status = subject.expiryStatusGiven(0, true, false);
        assertEquals(OK, status);
    }

    @Test
    void ifContractExpiryNotEnabledItsOk() {
        final var status = subject.expiryStatusGiven(0, true, true);
        assertEquals(OK, status);
    }

    @Test
    void decodesKeyAsExpected() throws Exception {
        final var key = TxnHandlingScenario.SIMPLE_NEW_WACL_KT.asKey();
        wacl = TxnHandlingScenario.SIMPLE_NEW_WACL_KT.asJKey();
        assertTrue(equalUpToDecodability(wacl, subject.attemptDecodeOrThrow(key)));
    }

    @Test
    void throwsOnInvalidKey() {
        final var k = Key.getDefaultInstance();
        assertFailsWith(() -> subject.attemptDecodeOrThrow(k), BAD_ENCODING);
    }

    @Test
    void rejectsInappropriatelyLargeTotalNfts() {
        // setup:
        final long proposed = 101L;

        given(dynamicProperties.maxNftMints()).willReturn(proposed - 1);

        // expect:
        assertFalse(subject.isPermissibleTotalNfts(proposed));

        // and given:
        given(dynamicProperties.maxNftMints()).willReturn(proposed);

        // expect:
        assertTrue(subject.isPermissibleTotalNfts(proposed));
    }

    @Test
    void understandsBeforeAfterConsensusTime() {
        // expect:
        assertTrue(subject.isAfterConsensusSecond(now.getEpochSecond() + 1));
        assertFalse(subject.isAfterConsensusSecond(now.getEpochSecond()));
        assertFalse(subject.isAfterConsensusSecond(now.getEpochSecond() - 1));
    }

    @Test
    void nullConsTimeMeansAlwaysAfter() {
        given(txnCtx.consensusTime()).willReturn(null);
        assertTrue(subject.isAfterConsensusSecond(now.getEpochSecond()));
    }

    @Test
    void recognizesCurrentNodeAccount() {
        // expect:
        assertTrue(subject.isThisNodeAccount(thisNodeAccount));
        assertFalse(subject.isThisNodeAccount(a));
    }

    @Test
    void recognizesOkFile() throws Exception {
        given(view.infoForFile(target)).willReturn(Optional.of(asMinimalInfo(attr)));

        // when:
        final var status = subject.queryableFileStatus(target, view);

        // then:
        assertEquals(OK, status);
    }

    @Test
    void recognizesDeletedFile() throws Exception {
        given(view.infoForFile(target)).willReturn(Optional.of(asMinimalInfo(deletedAttr)));

        // when:
        final var status = subject.queryableFileStatus(target, view);

        // then:
        assertEquals(OK, status);
        assertTrue(deletedAttr.isDeleted());
    }

    @Test
    void recognizesMissingFile() {
        given(view.infoForFile(target)).willReturn(Optional.empty());

        // when:
        final var status = subject.queryableFileStatus(target, view);

        // then:
        assertEquals(INVALID_FILE_ID, status);
    }

    @Test
    void usesConsensusTimeForTopicExpiry() {
        // expect:
        assertTrue(subject.isExpired(expiredMerkleTopic));
        assertFalse(subject.isExpired(merkleTopic));
    }

    @Test
    void recognizesMissingTopic() {
        // expect:
        assertEquals(INVALID_TOPIC_ID, subject.queryableTopicStatus(missingTopicId, topics));
    }

    @Test
    void recognizesDeletedTopicStatus() {
        // expect:
        assertEquals(INVALID_TOPIC_ID, subject.queryableTopicStatus(deletedTopicId, topics));
    }

    @Test
    void ignoresExpiredTopicStatus() {
        // expect:
        assertEquals(OK, subject.queryableTopicStatus(expiredTopicId, topics));
    }

    @Test
    void recognizesOkTopicStatus() {
        // expect:
        assertEquals(OK, subject.queryableTopicStatus(topicId, topics));
    }

    @Test
    void recognizesMissingAccountStatus() {
        // expect:
        assertEquals(INVALID_ACCOUNT_ID, subject.queryableAccountStatus(missing, accounts));
    }

    @Test
    void recognizesDeletedAccountStatus() {
        // expect:
        assertEquals(ACCOUNT_DELETED, subject.queryableAccountStatus(deleted, accounts));
    }

    @Test
    void recognizesOutOfPlaceAccountStatus() {
        // expect:
        assertEquals(INVALID_ACCOUNT_ID, subject.queryableAccountStatus(IdUtils.asAccount("0.0.5432"), accounts));
    }

    @Test
    void recognizesOkAccountStatus() {
        // expect:
        assertEquals(OK, subject.queryableAccountStatus(a, accounts));
    }

    @Test
    void recognizesMissingContractStatus() {
        // expect:
        assertEquals(INVALID_CONTRACT_ID, subject.queryableContractStatus(missingContract, accounts));
    }

    @Test
    void recognizesMissingContractStatusAsNum() {
        // expect:
        assertEquals(
                INVALID_CONTRACT_ID,
                subject.queryableContractStatus(EntityNum.fromContractId(missingContract), accounts));
    }

    @Test
    void recognizesDeletedContractStatus() {
        // expect:
        assertEquals(CONTRACT_DELETED, subject.queryableContractStatus(deletedContract, accounts));
    }

    @Test
    void recognizesOutOfPlaceContractStatus() {
        // expect:
        assertEquals(INVALID_CONTRACT_ID, subject.queryableContractStatus(IdUtils.asContract("0.0.9999"), accounts));
    }

    @Test
    void recognizesOkContractStatus() {
        // expect:
        assertEquals(OK, subject.queryableContractStatus(contract, accounts));
    }

    @Test
    void rejectsBriefTxnDuration() {
        given(dynamicProperties.minTxnDuration()).willReturn(2L);
        given(dynamicProperties.maxTxnDuration()).willReturn(10L);

        // expect:
        assertFalse(subject.isValidTxnDuration(1L));
    }

    @Test
    void rejectsProlongedTxnDuration() {
        given(dynamicProperties.minTxnDuration()).willReturn(2L);
        given(dynamicProperties.maxTxnDuration()).willReturn(10L);

        // expect:
        assertFalse(subject.isValidTxnDuration(11L));
    }

    @Test
    void rejectsBriefAutoRenewPeriod() {
        // setup:
        final Duration autoRenewPeriod = Duration.newBuilder().setSeconds(55L).build();

        given(dynamicProperties.minAutoRenewDuration()).willReturn(1_000L);
        given(dynamicProperties.maxAutoRenewDuration()).willReturn(1_000_000L);

        // expect:
        assertFalse(subject.isValidAutoRenewPeriod(autoRenewPeriod));
        // and:
        verify(dynamicProperties).minAutoRenewDuration();
    }

    @Test
    void acceptsReasonablePeriod() {
        // setup:
        final Duration autoRenewPeriod =
                Duration.newBuilder().setSeconds(500_000L).build();

        given(dynamicProperties.minAutoRenewDuration()).willReturn(1_000L);
        given(dynamicProperties.maxAutoRenewDuration()).willReturn(1_000_000L);

        // expect:
        assertTrue(subject.isValidAutoRenewPeriod(autoRenewPeriod));
        // and:
        verify(dynamicProperties).minAutoRenewDuration();
        verify(dynamicProperties).maxAutoRenewDuration();
    }

    @Test
    void rejectsProlongedAutoRenewPeriod() {
        // setup:
        final Duration autoRenewPeriod =
                Duration.newBuilder().setSeconds(5_555_555L).build();

        given(dynamicProperties.minAutoRenewDuration()).willReturn(1_000L);
        given(dynamicProperties.maxAutoRenewDuration()).willReturn(1_000_000L);

        // expect:
        assertFalse(subject.isValidAutoRenewPeriod(autoRenewPeriod));
        // and:
        verify(dynamicProperties).minAutoRenewDuration();
        verify(dynamicProperties).maxAutoRenewDuration();
    }

    @Test
    void allowsReasonableLength() {
        // setup:
        final TransferList wrapper = withAdjustments(a, 2L, b, -3L, d, 1L);

        given(dynamicProperties.maxTransferListSize()).willReturn(3);

        // expect:
        assertTrue(subject.isAcceptableTransfersLength(wrapper));
    }

    @Test
    void rejectsUnreasonableLength() {
        // setup:
        final TransferList wrapper = withAdjustments(a, 2L, b, -3L, d, 1L);

        given(dynamicProperties.maxTransferListSize()).willReturn(2);

        // expect:
        assertFalse(subject.isAcceptableTransfersLength(wrapper));
    }

    @Test
    void acceptsMappableKey() {
        // expect:
        assertTrue(subject.hasGoodEncoding(key));
    }

    @Test
    void rejectsUnmappableKey() {
        // expect:
        assertFalse(subject.hasGoodEncoding(Key.getDefaultInstance()));
    }

    @Test
    void acceptsEmptyKeyList() {
        // expect:
        assertTrue(subject.hasGoodEncoding(
                Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build()));
    }

    @Test
    void rejectsFutureExpiryImplyingSuperMaxLifetime() {
        // given:
        final var excessive = Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond() + maxLifetime + 1L)
                .build();

        // expect:
        assertFalse(subject.isValidExpiry(excessive));
        // and:
        verify(txnCtx).consensusTime();
    }

    @Test
    void allowsFutureExpiryBeforeMaxLifetime() {
        // expect:
        assertTrue(subject.isValidExpiry(Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano() + 1)
                .build()));
        // and:
        verify(txnCtx).consensusTime();
    }

    @Test
    void rejectsAnyNonFutureExpiry() {
        // expect:
        assertFalse(subject.isValidExpiry(Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build()));

        // and:
        verify(txnCtx).consensusTime();
    }

    @Test
    void recognizesExpiredCondition() {
        final SignedTxnAccessor accessor = mock(SignedTxnAccessor.class);

        // given:
        final long validDuration = 1_000L;
        final Instant validStart = Instant.ofEpochSecond(1_234_567L);
        final Instant consensusTime = Instant.ofEpochSecond(validStart.getEpochSecond() + validDuration + 1);
        // and:
        final TransactionID txnId = TransactionID.newBuilder()
                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(validStart.getEpochSecond()))
                .build();
        final TransactionBody txn = TransactionBody.newBuilder()
                .setTransactionID(txnId)
                .setTransactionValidDuration(Duration.newBuilder().setSeconds(validDuration))
                .build();
        // and:
        given(accessor.getTxn()).willReturn(txn);
        given(accessor.getTxnId()).willReturn(txnId);

        // when:
        final ResponseCodeEnum status = subject.chronologyStatus(accessor, consensusTime);

        // then:
        assertEquals(TRANSACTION_EXPIRED, status);
        // and:
        assertEquals(TRANSACTION_EXPIRED, subject.chronologyStatusForTxn(validStart, validDuration, consensusTime));
    }

    @Test
    void recognizesFutureValidStartStart() {
        final SignedTxnAccessor accessor = mock(SignedTxnAccessor.class);

        // given:
        final long validDuration = 1_000L;
        final Instant consensusTime = Instant.ofEpochSecond(1_234_567L);
        final Instant validStart =
                Instant.ofEpochSecond(consensusTime.plusSeconds(1L).getEpochSecond());
        // and:
        final TransactionID txnId = TransactionID.newBuilder()
                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(validStart.getEpochSecond()))
                .build();
        final TransactionBody txn = TransactionBody.newBuilder()
                .setTransactionID(txnId)
                .setTransactionValidDuration(Duration.newBuilder().setSeconds(validDuration))
                .build();
        // and:
        given(accessor.getTxn()).willReturn(txn);
        given(accessor.getTxnId()).willReturn(txnId);

        // when:
        final ResponseCodeEnum status = subject.chronologyStatus(accessor, consensusTime);

        // then:
        assertEquals(INVALID_TRANSACTION_START, status);
        // and:
        assertEquals(
                INVALID_TRANSACTION_START, subject.chronologyStatusForTxn(validStart, validDuration, consensusTime));
    }

    @Test
    void acceptsOk() {
        final SignedTxnAccessor accessor = mock(SignedTxnAccessor.class);

        // given:
        final long validDuration = 1_000L;
        final Instant consensusTime = Instant.ofEpochSecond(1_234_567L);
        final Instant validStart = Instant.ofEpochSecond(
                consensusTime.minusSeconds(validDuration - 1).getEpochSecond());
        // and:
        final TransactionID txnId = TransactionID.newBuilder()
                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(validStart.getEpochSecond()))
                .build();
        final TransactionBody txn = TransactionBody.newBuilder()
                .setTransactionID(txnId)
                .setTransactionValidDuration(Duration.newBuilder().setSeconds(validDuration))
                .build();
        // and:
        given(accessor.getTxn()).willReturn(txn);
        given(accessor.getTxnId()).willReturn(txnId);

        // when:
        final ResponseCodeEnum status = subject.chronologyStatus(accessor, consensusTime);

        // then:
        assertEquals(OK, status);
    }

    @Test
    void validatesStakingId() {
        final var deletedAccount = new MerkleAccount();
        deletedAccount.setDeleted(true);
        final NodeInfo nodeInfo = mock(NodeInfo.class);

        given(nodeInfo.isValidId(10L)).willReturn(true);
        CryptoCreateTransactionBody op =
                CryptoCreateTransactionBody.newBuilder().setStakedNodeId(10L).build();
        assertEquals(
                true,
                subject.isValidStakedId(
                        op.getStakedIdCase().name(),
                        op.getStakedAccountId(),
                        op.getStakedNodeId(),
                        accounts,
                        nodeInfo));

        op = CryptoCreateTransactionBody.newBuilder().setStakedNodeId(10L).build();
        given(nodeInfo.isValidId(10L)).willReturn(false);
        assertEquals(
                false,
                subject.isValidStakedId(
                        op.getStakedIdCase().name(),
                        op.getStakedAccountId(),
                        op.getStakedNodeId(),
                        accounts,
                        nodeInfo));
    }

    @Test
    void validatesStakingAccountId() {
        final var deletedAccount = new MerkleAccount();
        deletedAccount.setDeleted(true);
        final NodeInfo nodeInfo = mock(NodeInfo.class);

        given(accounts.get(EntityNum.fromLong(10L))).willReturn(new MerkleAccount());
        CryptoCreateTransactionBody op = CryptoCreateTransactionBody.newBuilder()
                .setStakedAccountId(asAccount("0.0.10"))
                .build();
        assertEquals(
                true,
                subject.isValidStakedId(
                        op.getStakedIdCase().name(),
                        op.getStakedAccountId(),
                        op.getStakedNodeId(),
                        accounts,
                        nodeInfo));

        given(accounts.get(EntityNum.fromLong(10L))).willReturn(deletedAccount);
        op = CryptoCreateTransactionBody.newBuilder()
                .setStakedAccountId(asAccount("0.0.10"))
                .build();
        assertEquals(
                false,
                subject.isValidStakedId(
                        op.getStakedIdCase().name(),
                        op.getStakedAccountId(),
                        op.getStakedNodeId(),
                        accounts,
                        nodeInfo));

        given(accounts.get(EntityNum.fromLong(10L))).willReturn(null);
        op = CryptoCreateTransactionBody.newBuilder()
                .setStakedAccountId(asAccount("0.0.10"))
                .build();
        assertEquals(
                false,
                subject.isValidStakedId(
                        op.getStakedIdCase().name(),
                        op.getStakedAccountId(),
                        op.getStakedNodeId(),
                        accounts,
                        nodeInfo));
    }

    @Test
    void rejectsImplausibleAccounts() {
        // given:
        final var implausibleShard = AccountID.newBuilder().setShardNum(-1).build();
        final var implausibleRealm = AccountID.newBuilder().setRealmNum(-1).build();
        final var implausibleAccount = AccountID.newBuilder().setAccountNum(0).build();
        final var plausibleAccount = IdUtils.asAccount("0.0.13257");

        // expect:
        assertFalse(subject.isPlausibleAccount(implausibleShard));
        assertFalse(subject.isPlausibleAccount(implausibleRealm));
        assertFalse(subject.isPlausibleAccount(implausibleAccount));
        assertTrue(subject.isPlausibleAccount(plausibleAccount));
    }

    @Test
    void rejectsImplausibleTxnFee() {
        // expect:
        assertFalse(subject.isPlausibleTxnFee(-1));
        assertTrue(subject.isPlausibleTxnFee(0));
    }

    @Test
    void acceptsReasonableTokenSymbol() {
        given(dynamicProperties.maxTokenSymbolUtf8Bytes()).willReturn(3);

        // expect:
        assertEquals(OK, subject.tokenSymbolCheck("AS"));
    }

    @Test
    void rejectsMalformedTokenSymbol() {
        given(dynamicProperties.maxTokenSymbolUtf8Bytes()).willReturn(100);

        // expect:
        assertEquals(MISSING_TOKEN_SYMBOL, subject.tokenSymbolCheck(""));
        assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.tokenSymbolCheck("\u0000"));
    }

    @Test
    void rejectsTooLongTokenSymbol() {
        given(dynamicProperties.maxTokenSymbolUtf8Bytes()).willReturn(3);

        // expect:
        assertEquals(TOKEN_SYMBOL_TOO_LONG, subject.tokenSymbolCheck("A€"));
    }

    @Test
    void acceptsReasonableTokenName() {
        given(dynamicProperties.maxTokenNameUtf8Bytes()).willReturn(100);

        // expect:
        assertEquals(OK, subject.tokenNameCheck("ASDF"));
    }

    @Test
    void rejectsMissingTokenName() {
        // expect:
        assertEquals(MISSING_TOKEN_NAME, subject.tokenNameCheck(""));
    }

    @Test
    void rejectsMalformedTokenName() {
        given(dynamicProperties.maxTokenNameUtf8Bytes()).willReturn(3);

        // expect:
        assertEquals(TOKEN_NAME_TOO_LONG, subject.tokenNameCheck("A€"));
        assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.tokenNameCheck("\u0000"));
    }

    @Test
    void memoCheckWorks() {
        final char[] aaa = new char[101];
        Arrays.fill(aaa, 'a');

        // expect:
        assertEquals(OK, subject.memoCheck("OK"));
        assertEquals(MEMO_TOO_LONG, subject.memoCheck(new String(aaa)));
        assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.memoCheck("Not s\u0000 ok!"));
    }

    @Test
    void rejectsInvalidBurnBatchSize() {
        given(dynamicProperties.maxBatchSizeBurn()).willReturn(10);
        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, subject.maxBatchSizeBurnCheck(12));
    }

    @Test
    void rejectsInvalidNftTransfersSize() {
        given(dynamicProperties.maxNftTransfersLen()).willReturn(10);
        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, subject.maxNftTransfersLenCheck(12));
    }

    @Test
    void rejectsInvalidWipeBatchSize() {
        given(dynamicProperties.maxBatchSizeWipe()).willReturn(10);
        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, subject.maxBatchSizeWipeCheck(12));
    }

    @Test
    void rejectsInvalidMintBatchSize() {
        given(dynamicProperties.maxBatchSizeMint()).willReturn(10);
        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, subject.maxBatchSizeMintCheck(12));
    }

    @Test
    void rejectsInvalidQueryRange() {
        given(dynamicProperties.maxNftQueryRange()).willReturn(10L);
        assertEquals(INVALID_QUERY_RANGE, subject.nftMaxQueryRangeCheck(0, 11));
    }

    @Test
    void rejectsInvalidMetadata() {
        given(dynamicProperties.maxNftMetadataBytes()).willReturn(2);
        assertEquals(METADATA_TOO_LONG, subject.nftMetadataCheck(new byte[] {1, 2, 3, 4}));
    }

    @Test
    void acceptsValidQueryRange() {
        given(dynamicProperties.maxNftQueryRange()).willReturn(10L);
        assertEquals(OK, subject.nftMaxQueryRangeCheck(0, 9));
    }

    @Test
    void rejectsDecodeEmptyKey() {
        assertFailsWith(() -> subject.attemptToDecodeOrThrow(Key.getDefaultInstance(), BAD_ENCODING), BAD_ENCODING);
    }
}
