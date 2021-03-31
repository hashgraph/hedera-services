package com.hedera.services.files;

import com.hedera.services.files.sysfiles.ConfigCallbacks;
import com.hedera.services.files.sysfiles.CurrencyCallbacks;
import com.hedera.services.files.sysfiles.ThrottlesCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class SysFileCallbacksTest {
	@Mock
	ConfigCallbacks configCallbacks;
	@Mock
	ThrottlesCallback throttlesCallback;
	@Mock
	CurrencyCallbacks currencyCallbacks;

	SysFileCallbacks subject;

	@BeforeEach
	void setUp() {
		subject = new SysFileCallbacks(configCallbacks, throttlesCallback, currencyCallbacks);
	}

	@Test
	void delegatesAsExpected() {
		var inOrder = inOrder(configCallbacks, throttlesCallback, currencyCallbacks);

		// when:
		subject.permissionsCb();
		subject.propertiesCb();
		subject.throttlesCb();
		subject.exchangeRatesCb();
		subject.feeSchedulesCb();

		// verify:
		inOrder.verify(configCallbacks).permissionsCb();
		inOrder.verify(configCallbacks).propertiesCb();
		inOrder.verify(throttlesCallback).throttlesCb();
		inOrder.verify(currencyCallbacks).exchangeRatesCb();
		inOrder.verify(currencyCallbacks).feeSchedulesCb();
	}
}