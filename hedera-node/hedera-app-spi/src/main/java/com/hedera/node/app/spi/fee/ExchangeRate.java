package com.hedera.node.app.spi.fee;

import java.math.BigDecimal;

public interface ExchangeRate {
	BigDecimal exchange(Currency from, Currency to, long amount);
}
