package com.hedera.services.utils.accessors.custom;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.utils.KeyUtils;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.utils.accessors.SignedTxnAccessorTest.buildTransactionFrom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willCallRealMethod;

@ExtendWith(MockitoExtension.class)
public class CryptoCreateAccessorTest {
	private static final Key adminKey = KeyUtils.A_THRESHOLD_KEY;
	private static final long autoRenewPeriod = 1_234_567L;
	private static final long now = 1_234_567L;
	private static final String memo = "Eternal sunshine of the spotless mind";

	@Mock private AccessorFactory accessorFactory;

	@Test
	void setCryptoCreateUsageMetaWorks() {
		final var txn = signedCryptoCreateTxn();
		final var accessor = getAccessor(txn);
		final var spanMapAccessor = accessor.getSpanMapAccessor();

		final var expandedMeta = spanMapAccessor.getCryptoCreateMeta(accessor);

		assertEquals(137, expandedMeta.getBaseSize());
		assertEquals(autoRenewPeriod, expandedMeta.getLifeTime());
		assertEquals(10, expandedMeta.getMaxAutomaticAssociations());
	}

	private SignedTxnAccessor getAccessor(final Transaction txn){
		try{
			willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(any());
			return accessorFactory.constructSpecializedAccessor(txn.toByteArray());
		} catch (InvalidProtocolBufferException e) {
			throw new IllegalStateException(e);
		}
	}

	private Transaction signedCryptoCreateTxn() {
		return buildTransactionFrom(cryptoCreateOp());
	}

	private TransactionBody cryptoCreateOp() {
		final var op =
				CryptoCreateTransactionBody.newBuilder()
						.setMemo(memo)
						.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
						.setKey(adminKey)
						.setMaxAutomaticTokenAssociations(10);
		return TransactionBody.newBuilder()
				.setTransactionID(
						TransactionID.newBuilder()
								.setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
				.setCryptoCreateAccount(op)
				.build();
	}
}
