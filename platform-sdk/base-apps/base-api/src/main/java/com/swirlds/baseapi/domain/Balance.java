package com.swirlds.baseapi.domain;

import com.swirlds.baseapi.persistence.Version;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigDecimal;

public record Balance(@NonNull Wallet wallet, @NonNull BigDecimal amount, @NonNull Version version) {

}
