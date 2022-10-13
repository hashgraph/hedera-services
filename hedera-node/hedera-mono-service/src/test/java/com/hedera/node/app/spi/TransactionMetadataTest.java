package com.hedera.node.app.spi;

import com.hedera.services.legacy.core.jproto.JKey;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
public class TransactionMetadataTest {
	private Timestamp consensusTimestamp = Timestamp.newBuilder().setSeconds(1_234_567L).build();
	private Key key = KeyUtils.A_COMPLEX_KEY;
	private AccountID payer = asAccount("0.0.3");
	private TransactionMetadata subject;

	@Test
	void gettersWorkAsExpectedWhenOtherSigsDoesntExist() throws DecoderException {
		final var txn = createAccountTransaction();
		final var payerKey = JKey.mapKey(key);
		subject = new TransactionMetadata(txn, false, payerKey);

		assertFalse(subject.failed());
		assertEquals(txn, subject.transaction());
		assertEquals(payerKey, subject.getPayerSig());
		assertEquals(Collections.emptyList(), subject.getOthersSigs());
	}

	@Test
	void gettersWorkAsExpectedWhenOtherSigsExist() throws DecoderException {
		final var txn = createAccountTransaction();
		final var payerKey = JKey.mapKey(key);
		subject = new TransactionMetadata(txn, false, payerKey, List.of(payerKey));

		assertFalse(subject.failed());
		assertEquals(txn, subject.transaction());
		assertEquals(payerKey, subject.getPayerSig());
		assertEquals(List.of(payerKey), subject.getOthersSigs());
	}


	private Transaction createAccountTransaction(){
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
