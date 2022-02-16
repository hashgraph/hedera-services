package com.hedera.services.utils.accessors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAccountWithAlias;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoCreateAccessorTest {
	@Mock
	AliasManager aliasManager;

	private final long initialBalance = 100L;
	private final long payerNum = 345L;
	private final long proxyNum = 456L;
	private final int maxAutoAssociations = 10;
	private final EntityNum payerEntity = EntityNum.fromLong(payerNum);
	private final EntityNum proxyEntity = EntityNum.fromLong(proxyNum);
	private final AccountID payer = asAccount("0.0." + payerNum);
	private final AccountID aliasedPayer = asAccountWithAlias("All is well that ends better");
	private final AccountID proxy = asAccount("0.0." + proxyNum);
	private final AccountID aliasedProxy = asAccountWithAlias("Courage is found in unlikely places");
	public  final long autoRenewDuration = 7776000L;
	private final Key aKey = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();

	private CryptoCreateAccessor subject;
	private SwirldTransaction accountCreateTxn;


	@Test
	void fetchesDataAsExpected() throws InvalidProtocolBufferException {
		setUpWith(payer, proxy);
		when(aliasManager.unaliased(payer)).thenReturn(payerEntity);
		when(aliasManager.unaliased(proxy)).thenReturn(proxyEntity);

		subject = new CryptoCreateAccessor(accountCreateTxn, aliasManager);

		validate();
	}

	@Test
	void fetchesAliasedDataAsExpected() throws InvalidProtocolBufferException {
		setUpWith(aliasedPayer, aliasedProxy);
		when(aliasManager.unaliased(aliasedPayer)).thenReturn(payerEntity);
		when(aliasManager.unaliased(aliasedProxy)).thenReturn(proxyEntity);

		subject = new CryptoCreateAccessor(accountCreateTxn, aliasManager);

		validate();
	}

	@Test
	void fetchesMissingAliasAsExpected() throws InvalidProtocolBufferException {
		setUpWith(aliasedPayer, aliasedProxy);
		when(aliasManager.unaliased(aliasedPayer)).thenReturn(EntityNum.MISSING_NUM);
		when(aliasManager.unaliased(aliasedProxy)).thenReturn(EntityNum.MISSING_NUM);

		subject = new CryptoCreateAccessor(accountCreateTxn, aliasManager);

		assertEquals(0L, subject.getSponsor().getAccountNum());
		assertEquals(0L, subject.getProxy().getAccountNum());
	}

	private void validate() {
		assertEquals(payer, subject.getSponsor());
		assertEquals(initialBalance, subject.getInitialBalance());
		assertEquals(maxAutoAssociations, subject.getMaxAutomaticTokenAssociations());
		assertEquals(aKey, subject.getKey());
		assertEquals(proxy, subject.getProxy());
	}

	private void setUpWith(AccountID payer, AccountID proxy) {
		final var txnBody = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder().setAccountID(payer).build())
				.setCryptoCreateAccount(CryptoCreateTransactionBody.newBuilder()
						.setInitialBalance(initialBalance)
						.setProxyAccountID(proxy)
						.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewDuration))
						.setMaxAutomaticTokenAssociations(maxAutoAssociations)
						.setKey(aKey)
						.setMemo("Not all those who wander are lost")
						.build())
				.build();
		accountCreateTxn = new SwirldTransaction(Transaction.newBuilder()
				.setBodyBytes(txnBody.toByteString())
				.build().toByteArray());
	}

}
