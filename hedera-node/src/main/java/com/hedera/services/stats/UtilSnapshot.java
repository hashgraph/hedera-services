package com.hedera.services.stats;

import java.util.function.DoubleSupplier;

public record UtilSnapshot(String utilType, DoubleSupplier valueSource) {
}
