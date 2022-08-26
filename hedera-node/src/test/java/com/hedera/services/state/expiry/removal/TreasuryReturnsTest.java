package com.hedera.services.state.expiry.removal;

import com.hedera.services.throttling.ExpiryThrottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class TreasuryReturnsTest {
    @Mock
    private ExpiryThrottle expiryThrottle;
    @Mock
    private TreasuryReturnHelper returnHelper;

    private TreasuryReturns subject;

    @BeforeEach
    void setUp() {
        subject = new TreasuryReturns(expiryThrottle,returnHelper);
      }


}