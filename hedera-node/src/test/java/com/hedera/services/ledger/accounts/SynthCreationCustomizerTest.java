package com.hedera.services.ledger.accounts;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SynthCreationCustomizerTest {
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

	private SynthCreationCustomizer subject;

	@BeforeEach
	void setUp() {
		subject = new SynthCreationCustomizer(accountsLedger);
	}

	@Test
	void customizesAsExpected() {
		given(accountsLedger.get(callerId, KEY)).willReturn(cryptoAdminKey);
		given(accountsLedger.get(callerId, MEMO)).willReturn(memo);
		given(accountsLedger.get(callerId, EXPIRY)).willReturn(expiry);
		given(accountsLedger.get(callerId, PROXY)).willReturn(proxy);
		given(accountsLedger.get(callerId, AUTO_RENEW_PERIOD)).willReturn(autoRenewPeriod);
		given(accountsLedger.get(callerId, AUTO_RENEW_ACCOUNT_ID)).willReturn(autoRenewAccount);
		given(accountsLedger.get(callerId, MAX_AUTOMATIC_ASSOCIATIONS)).willReturn(maxAutoAssociations);

		final var origCreation = TransactionBody.newBuilder()
				.setContractCreateInstance(ContractCreateTransactionBody.getDefaultInstance())
				.build();
		final var customCreation = subject.customize(origCreation, callerId);

		final var customOp = customCreation.getContractCreateInstance();
		assertEquals(grpcCryptoAdminKey, customOp.getAdminKey());
		assertEquals(memo, customOp.getMemo());
		assertEquals(autoRenewPeriod, customOp.getAutoRenewPeriod().getSeconds());
		assertEquals(autoRenewAccount.toGrpcAccountId(), customOp.getAutoRenewAccountId());
		assertEquals(proxy.toGrpcAccountId(), customOp.getProxyAccountID());
	}

	private static final JKey cryptoAdminKey = new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
	private static final Key grpcCryptoAdminKey = MiscUtils.asKeyUnchecked(cryptoAdminKey);
	private static final AccountID callerId = asAccount("0.0.666");
	private static final long expiry = 1_234_567L;
	private static final long autoRenewPeriod = 7776000L;
	private static final String memo = "the grey rock";
	private static final EntityId proxy = new EntityId(0, 0, 3);
	private static final EntityId autoRenewAccount = new EntityId(0, 0, 4);
	private static final int maxAutoAssociations = 25;
}