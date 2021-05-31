package com.hedera.services.sigs;

import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.sigs.order.SigningOrderResultFactory;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;

import static com.hedera.services.sigs.Rationalization.IN_HANDLE_SUMMARY_FACTORY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RationalizationTest {
	private final TransactionID txnId = TransactionID.getDefaultInstance();
	private final TransactionBody txn = TransactionBody.getDefaultInstance();
	private final SigningOrderResult<SignatureStatus> generalError = IN_HANDLE_SUMMARY_FACTORY.forGeneralError(txnId);

	@Mock
	private SwirldTransaction swirldsTxn;
	@Mock
	private TxnAccessor txnAccessor;
	@Mock
	private SyncVerifier syncVerifier;
	@Mock
	private HederaSigningOrder keyOrderer;
	@Mock
	private TxnScopedPlatformSigFactory sigFactory;
	@Mock
	private PubKeyToSigBytes pkToSigFn;

	private Rationalization subject;

	@BeforeEach
	void setUp() {
		given(txnAccessor.getTxn()).willReturn(txn);
		given(txnAccessor.getPlatformTxn()).willReturn(swirldsTxn);

		subject = new Rationalization(txnAccessor, syncVerifier, keyOrderer, pkToSigFn, sigFactory);
	}

	@Test
	void setsUnavailableMetaIfCannotListPayerKey() {
		// setup:
		ArgumentCaptor<RationalizedSigMeta> captor = ArgumentCaptor.forClass(RationalizedSigMeta.class);

		given(keyOrderer.keysForPayer(txn, IN_HANDLE_SUMMARY_FACTORY)).willReturn(generalError);

		// when:
		final var result = subject.execute();

		// then:
		assertEquals(result, generalError.getErrorReport());
		// and:
		verify(txnAccessor).setSigMeta(captor.capture());
		assertSame(RationalizedSigMeta.noneAvailable(), captor.getValue());
	}
}