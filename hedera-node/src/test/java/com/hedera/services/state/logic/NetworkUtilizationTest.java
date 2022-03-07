package com.hedera.services.state.logic;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.fees.charging.TxnChargingPolicyAgent;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.services.state.logic.NetworkUtilization.STAND_IN_CRYPTO_TRANSFER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class NetworkUtilizationTest {
	private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);

	@Mock
	private TransactionContext txnCtx;
	@Mock
	private FeeMultiplierSource feeMultiplierSource;
	@Mock
	private TxnChargingPolicyAgent chargingPolicyAgent;
	@Mock
	private FunctionalityThrottling handleThrottling;
	@Mock
	private TxnAccessor accessor;

	private NetworkUtilization subject;

	@BeforeEach
	void setUp() {
		subject = new NetworkUtilization(txnCtx, feeMultiplierSource, chargingPolicyAgent, handleThrottling);
	}

	@Test
	void tracksUserTxnAsExpected() {
		subject.trackUserTxn(accessor, consensusNow);

		verify(handleThrottling).shouldThrottleTxn(accessor);
		verify(feeMultiplierSource).updateMultiplier(consensusNow);
	}

	@Test
	void tracksFeePaymentsAsExpected() {
		subject.trackFeePayments(consensusNow);

		verify(handleThrottling).shouldThrottleTxn(STAND_IN_CRYPTO_TRANSFER);
		verify(feeMultiplierSource).updateMultiplier(consensusNow);
	}


	@Test
	void standInCryptoTransferHasExpectedProperties() {
		assertEquals(HederaFunctionality.CryptoTransfer, STAND_IN_CRYPTO_TRANSFER.getFunction());
		assertTrue(STAND_IN_CRYPTO_TRANSFER.areAutoCreationsCounted());
	}

	@Test
	void happyPathWorks() {
		assertTrue(subject.screenForAvailableCapacity());
	}

	@Test
	void rejectsAsExpectedAfterGasThrottledTxn() {
		given(handleThrottling.wasLastTxnGasThrottled()).willReturn(true);

		assertFalse(subject.screenForAvailableCapacity());

		verify(txnCtx).setStatus(CONSENSUS_GAS_EXHAUSTED);
		verify(chargingPolicyAgent).refundPayerServiceFee();
	}
}