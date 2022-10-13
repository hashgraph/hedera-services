package com.hedera.node.app.service.token.impl;

import com.google.protobuf.ByteString;
import com.hedera.node.app.spi.TransactionMetadata;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
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

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.TxnUtils.buildTransactionFrom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class CryptoPreTransactionHandlerImplTest {
	private Key key = KeyUtils.A_COMPLEX_KEY;
	private Timestamp consensusTimestamp = Timestamp.newBuilder().setSeconds(1_234_567L).build();
	private AccountID payer = asAccount("0.0.3");
	private final SignatureMap expectedMap =
			SignatureMap.newBuilder()
					.addSigPair(
							SignaturePair.newBuilder()
									.setPubKeyPrefix(ByteString.copyFromUtf8("f"))
									.setEd25519(ByteString.copyFromUtf8("irst")))
					.build();

	@Mock private AccountStore store;
	private CryptoPreTransactionHandlerImpl subject;

	@BeforeEach
	public void setUp(){
		subject = new CryptoPreTransactionHandlerImpl(store);
	}

	@Test
	void preHandlesCryptoCreate() throws DecoderException {
		final var jkey = JKey.mapKey(key);

		final var txn = createAccountTransaction(payer);
		final var expectedMeta = new TransactionMetadata(txn, false, jkey);
		given(store.createAccountSigningMetadata(txn, payer)).willReturn(expectedMeta);

		final var meta = subject.cryptoCreate(txn);

		assertEquals(expectedMeta, meta);
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
				.setCryptoCreateAccount(createTxnBody)
				.build();
		return buildTransactionFrom(transactionBody);
	}
}
