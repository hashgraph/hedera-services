package com.hedera.services.utils.accessors.custom;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.services.utils.accessors.SignedTxnAccessorTest.buildDefaultCryptoCreateTxn;
import static com.hedera.services.utils.accessors.SignedTxnAccessorTest.buildTransactionFrom;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willCallRealMethod;

@ExtendWith(MockitoExtension.class)
public class CryptoTransferAccessorTest {
	private static final String memo = "Eternal sunshine of the spotless mind";
	private static final long now = 1_234_567L;
	private static final AccountID a = asAccount("1.2.3");
	private static final AccountID b = asAccount("2.3.4");
	private static final AccountID c = asAccount("3.4.5");
	private static final TokenID anId = IdUtils.asToken("0.0.75231");
	private static final TokenID anotherId = IdUtils.asToken("0.0.75232");
	private static final TokenID yetAnotherId = IdUtils.asToken("0.0.75233");

	@Mock private AccessorFactory accessorFactory;

	@Test
	void fetchesSubTypeAsExpected() throws InvalidProtocolBufferException {
		final var nftTransfers =
				TokenTransferList.newBuilder()
						.setToken(anId)
						.addNftTransfers(
								NftTransfer.newBuilder()
										.setSenderAccountID(a)
										.setReceiverAccountID(b)
										.setSerialNumber(1))
						.build();
		final var fungibleTokenXfers =
				TokenTransferList.newBuilder()
						.setToken(anotherId)
						.addAllTransfers(
								List.of(adjustFrom(a, -50), adjustFrom(b, 25), adjustFrom(c, 25)))
						.build();

		var txn = buildTokenTransferTxn(nftTransfers);

		var subject = getAccessor(txn);;
		assertEquals(SubType.TOKEN_NON_FUNGIBLE_UNIQUE, subject.getSpanMapAccessor().getCryptoTransferMeta(subject).getSubType());
		assertEquals(subject.getSpanMapAccessor().getCryptoTransferMeta(subject).getSubType(), subject.getSubType());

		// set customFee
		var xferUsageMeta = subject.getSpanMapAccessor().getCryptoTransferMeta(subject);
		xferUsageMeta.setCustomFeeHbarTransfers(1);
		assertEquals(SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES, subject.getSubType());
		xferUsageMeta.setCustomFeeHbarTransfers(0);

		txn = buildTokenTransferTxn(fungibleTokenXfers);
		subject = getAccessor(txn);;
		assertEquals(TOKEN_FUNGIBLE_COMMON, subject.getSpanMapAccessor().getCryptoTransferMeta(subject).getSubType());
		assertEquals(subject.getSpanMapAccessor().getCryptoTransferMeta(subject).getSubType(), subject.getSubType());

		// set customFee
		xferUsageMeta = subject.getSpanMapAccessor().getCryptoTransferMeta(subject);
		xferUsageMeta.setCustomFeeTokenTransfers(1);
		assertEquals(SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES, subject.getSubType());
		xferUsageMeta.setCustomFeeTokenTransfers(0);

		txn = buildDefaultCryptoCreateTxn();
		subject = getAccessor(txn);;
		assertEquals(SubType.DEFAULT, subject.getSubType());
	}

	@Test
	void understandsFullXferUsageIncTokens() {
		final var txn = buildTransactionFrom(tokenXfers());
		final var subject = getAccessor(txn);

		final var xferMeta = subject.getSpanMapAccessor().getCryptoTransferMeta(subject);

		assertEquals(1, xferMeta.getTokenMultiplier());
		assertEquals(3, xferMeta.getNumTokensInvolved());
		assertEquals(7, xferMeta.getNumFungibleTokenTransfers());
	}

	private Transaction buildTokenTransferTxn(final TokenTransferList tokenTransferList) {
		final var op =
				CryptoTransferTransactionBody.newBuilder()
						.addTokenTransfers(tokenTransferList)
						.build();
		final var txnBody =
				TransactionBody.newBuilder()
						.setMemo(memo)
						.setTransactionID(
								TransactionID.newBuilder()
										.setTransactionValidStart(
												Timestamp.newBuilder().setSeconds(now)))
						.setCryptoTransfer(op)
						.build();

		return buildTransactionFrom(txnBody);
	}

	private SignedTxnAccessor getAccessor(final Transaction txn){
		try{
			willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(any());
			return accessorFactory.constructSpecializedAccessor(txn.toByteArray());
		} catch (InvalidProtocolBufferException e) {
			throw new IllegalStateException(e);
		}
	}

	private TransactionBody tokenXfers() {
		final var hbarAdjusts =
				TransferList.newBuilder()
						.addAccountAmounts(adjustFrom(a, -100))
						.addAccountAmounts(adjustFrom(b, 50))
						.addAccountAmounts(adjustFrom(c, 50))
						.build();
		final var op =
				CryptoTransferTransactionBody.newBuilder()
						.setTransfers(hbarAdjusts)
						.addTokenTransfers(
								TokenTransferList.newBuilder()
										.setToken(anotherId)
										.addAllTransfers(
												List.of(
														adjustFrom(a, -50),
														adjustFrom(b, 25),
														adjustFrom(c, 25))))
						.addTokenTransfers(
								TokenTransferList.newBuilder()
										.setToken(anId)
										.addAllTransfers(
												List.of(adjustFrom(b, -100), adjustFrom(c, 100))))
						.addTokenTransfers(
								TokenTransferList.newBuilder()
										.setToken(yetAnotherId)
										.addAllTransfers(
												List.of(adjustFrom(a, -15), adjustFrom(b, 15))))
						.build();

		return TransactionBody.newBuilder()
				.setMemo(memo)
				.setTransactionID(
						TransactionID.newBuilder()
								.setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
				.setCryptoTransfer(op)
				.build();
	}

	private AccountAmount adjustFrom(final AccountID account, final long amount) {
		return AccountAmount.newBuilder().setAmount(amount).setAccountID(account).build();
	}
}
