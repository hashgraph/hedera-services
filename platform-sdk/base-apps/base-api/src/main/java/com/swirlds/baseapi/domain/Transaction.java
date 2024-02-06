package com.swirlds.baseapi.domain;

import java.math.BigDecimal;

public record Transaction(String id, String from, String to, BigDecimal amount) {
}
