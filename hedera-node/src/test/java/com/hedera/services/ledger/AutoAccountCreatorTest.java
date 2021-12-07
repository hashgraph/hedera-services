package com.hedera.services.ledger;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.accounts.AutoAccountsManager;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.backing.HashMapBackingAccounts;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.TxnAwareRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.txns.crypto.AutoAccountCreateLogic;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.keys.KeyFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;

import static com.hedera.services.txns.crypto.AutoAccountCreateLogic.isPrimitiveKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.hbarChange;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AutoAccountCreatorTest {
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	@Mock
	EntityCreator entityCreator;
	@Mock
	TxnAwareRecordsHistorian recordsHistorian;
	@Mock
	EntityIdSource entityIdSource;
	@Mock
	AutoAccountsManager autoAccounts;

	AutoAccountCreateLogic subject;

	private final BackingStore<AccountID, MerkleAccount> backingAccounts = new HashMapBackingAccounts();
	SyntheticTxnFactory syntheticTxnFactory = new SyntheticTxnFactory();

	private final Key aliasA = KeyFactory.getDefaultInstance().newEd25519();
	private final ByteString validAlias = aliasA.toByteString();
	private final ByteString inValidAlias = ByteString.copyFromUtf8("aaaa");
	private final ByteString emptyAlias = ByteString.EMPTY;


	private final AccountID a = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(10L).build();
	private final AccountID validAliasAccount = AccountID.newBuilder().setAlias(validAlias).build();
	private final AccountID inValidAliasAccount = AccountID.newBuilder().setAlias(inValidAlias).build();
	final MerkleAccount aAccount = MerkleAccountFactory.newAccount().balance(100).get();
	private final BalanceChange validChange = hbarChange(validAliasAccount, +100);
	private final BalanceChange inValidChange = hbarChange(inValidAliasAccount, +100);

	@BeforeEach
	void setUp() {
		MockitoAnnotations.initMocks(this);
		backingAccounts.put(a, aAccount);
		subject = new AutoAccountCreateLogic(syntheticTxnFactory, entityCreator, entityIdSource, recordsHistorian, autoAccounts);
	}

	@Test
	void happyPathAutoCreates() {
		final var expirableTxnRecordBuilder = ExpirableTxnRecord.newBuilder()
				.setTxnId(TxnId.fromGrpc(TransactionID.newBuilder().setAccountID(asAccount("0.0.1001")).build()))
				.setReceipt(TxnReceipt.newBuilder().setStatus(OK.name()).build())
				.setMemo("test")
				.setConsensusTime(RichInstant.fromJava(Instant.now()));

		given(autoAccounts.getAutoAccountsMap()).willReturn(new HashMap<>());
		given(entityIdSource.newAccountId(any()))
				.willReturn(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(99).build());
		given(entityCreator.createSuccessfulSyntheticRecord(any(), any(), any())).willReturn(expirableTxnRecordBuilder);

		final var response = subject.createAutoAccount(validChange, accountsLedger);

		final var expectedCreatedAccount = new EntityNum(99);

		assertEquals(OK, response);
		assertEquals(expectedCreatedAccount, autoAccounts.getAutoAccountsMap().get(validAlias));
		assertEquals(1, subject.getTempCreations().size());
		assertEquals(expectedCreatedAccount.toGrpcAccountId(), subject.getTempCreations().get(validAlias));
	}

	@Test
	void invalidEncodedAlias() {
		final var response = subject.createAutoAccount(inValidChange, accountsLedger);

		assertEquals(BAD_ENCODING, response);
		assertEquals(null, autoAccounts.getAutoAccountsMap().get(validAlias));
		assertEquals(0, autoAccounts.getAutoAccountsMap().size());
		assertEquals(0, subject.getTempCreations().size());
	}

	@Test
	void validatesPrimitiveKey() {
		assertTrue(isPrimitiveKey(validAlias));
		assertFalse(isPrimitiveKey(inValidAlias));
		assertFalse(isPrimitiveKey(emptyAlias));
	}

}
