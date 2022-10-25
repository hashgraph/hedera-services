package com.hedera.services.evm.store.contracts;

import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class EvmTrackingAccountTest {
	private static final long newBalance = 200_000L;
	private static final int constNonce = 2;
    private EvmTrackingAccount subject;


    @BeforeEach
    void setUp() {
        subject = new EvmTrackingAccount(1, Wei.ONE);
    }

    @Test
    void mirrorsBalanceChangesInNonNullTrackingAccounts() {
		subject.setBalance(Wei.of(newBalance));
		assertEquals(newBalance, subject.getBalance().toLong());
    }

	@Test
	void getNonceReturnsExpectedValue(){
		 subject.setNonce(constNonce);
		assertEquals(constNonce,subject.getNonce());
	}
}
