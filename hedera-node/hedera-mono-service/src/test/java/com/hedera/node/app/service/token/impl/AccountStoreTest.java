package com.hedera.node.app.service.token.impl;

import com.google.protobuf.ByteString;
import com.hedera.node.app.spi.state.States;
import com.hedera.node.app.spi.state.impl.InMemoryStateImpl;
import com.hedera.node.app.spi.state.impl.RebuiltStateImpl;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static com.hedera.node.app.spi.state.StateKeys.ACCOUNT_STORE;
import static com.hedera.node.app.spi.state.StateKeys.ALIASES;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAliasAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class AccountStoreTest {
	private Key key = KeyUtils.A_COMPLEX_KEY;
	private Timestamp consensusTimestamp = Timestamp.newBuilder().setSeconds(1_234_567L).build();
	private AccountID payer = asAccount("0.0.3");
	private AccountID payerAlias = asAliasAccount(ByteString.copyFromUtf8("testAlias"));
	private EntityNum payerNum = EntityNum.fromInt(3);

	@Mock private RebuiltStateImpl aliases;
	@Mock private InMemoryStateImpl accounts;
	@Mock private MerkleAccount account;
	@Mock private States states;
	private AccountStore subject;

	@BeforeEach
	public void setUp(){
		given(states.get(ACCOUNT_STORE)).willReturn(accounts);
		given(states.get(ALIASES)).willReturn(aliases);
		subject = new AccountStore(states);
	}

	@Test
	void createAccountSigningMetaChecksAccounts() throws DecoderException {
		final var jkey = JKey.mapKey(key);
		given(accounts.get(payerNum)).willReturn(Optional.of(account));
		given(account.getAccountKey()).willReturn(jkey);

		final var txn = createAccountTransaction(payerAlias);
		final var meta = subject.createAccountSigningMetadata(txn, payer);

		assertFalse(meta.failed());
		assertEquals(txn, meta.transaction());
		assertEquals(jkey, meta.getPayerSig());
		assertEquals(Collections.emptyList(), meta.getOthersSigs());
	}

	@Test
	void createAccountSigningMetaChecksAlias() throws DecoderException {
		final var jkey = JKey.mapKey(key);
		given(aliases.get(payerAlias.getAlias())).willReturn(Optional.of(payerNum));
		given(accounts.get(payerNum)).willReturn(Optional.of(account));
		given(account.getAccountKey()).willReturn(jkey);

		final var txn = createAccountTransaction(payerAlias);
		final var meta = subject.createAccountSigningMetadata(txn, payerAlias);

		assertFalse(meta.failed());
		assertEquals(txn, meta.transaction());
		assertEquals(jkey, meta.getPayerSig());
		assertEquals(Collections.emptyList(), meta.getOthersSigs());
	}

	private Transaction createAccountTransaction(final AccountID payer){
		final var transactionID = TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(consensusTimestamp);
		final var createTxnBody = CryptoCreateTransactionBody.newBuilder()
				.setKey(key)
				.setReceiverSigRequired(true)
				.setMemo("Create Account")
				.build();

		final var transactionBody = TransactionBody.newBuilder()
				.setTransactionID(transactionID)
				.setCryptoCreateAccount(createTxnBody);
		final var signedTransaction = SignedTransaction.newBuilder()
				.setBodyBytes(transactionBody.build().toByteString());
		return Transaction.newBuilder()
						.setSignedTransactionBytes(signedTransaction.getBodyBytes())
						.build();
	}
}
