package com.hedera.node.app.workflows.handle.validation;

import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

public class StandardizedExpiryValidator implements ExpiryValidator {
    private final Consumer<Id> idValidator;
    private final LongSupplier consensusSecondNow;

    public StandardizedExpiryValidator(
            @NonNull final Consumer<Id> idValidator,
            @NonNull final LongSupplier consensusSecondNow) {
        this.idValidator = idValidator;
        this.consensusSecondNow = consensusSecondNow;
    }
}
