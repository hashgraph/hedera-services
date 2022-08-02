package com.hedera.services.utils.accessors.custom;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.utils.accessors.SignedTxnAccessorTest.buildTransactionFrom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willCallRealMethod;

@ExtendWith(MockitoExtension.class)
public class SubmitMessageAccessorTest {
	@Mock
	private AccessorFactory accessorFactory;
	@Test
	void understandsSubmitMessageMeta() {
		final var message = "And after, arranged it in a song";
		final var txnBody =
				TransactionBody.newBuilder()
						.setConsensusSubmitMessage(
								ConsensusSubmitMessageTransactionBody.newBuilder()
										.setMessage(ByteString.copyFromUtf8(message)))
						.build();
		final var txn = buildTransactionFrom(txnBody);
		final var subject = getAccessor(txn);

		final var submitMeta = subject.getSpanMapAccessor().getSubmitMessageMeta(subject);

		assertEquals(message.length(), submitMeta.numMsgBytes());
	}

	private SignedTxnAccessor getAccessor(final Transaction txn){
		try{
			willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(any());
			return accessorFactory.constructSpecializedAccessor(txn.toByteArray());
		} catch (InvalidProtocolBufferException e) {
			throw new IllegalStateException(e);
		}
	}
}
