package com.hedera.services.bdd.junit.validators;

import com.hedera.services.bdd.junit.BalanceReconciliationValidator;
import com.hedera.services.bdd.junit.RecordStreamValidator;
import com.hedera.services.bdd.junit.RecordWithSidecars;
import com.hedera.services.bdd.suites.utils.MiscEETUtils;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.CryptoTransfer;
import org.junit.jupiter.api.Assertions;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static com.hedera.services.bdd.junit.BalanceReconciliationValidator.streamOfItemsFrom;

/**
 * A simple validator that asserts an account was created at a given consensus timestamp.
 */
public class AccountExistenceValidator implements RecordStreamValidator {
    private final String name;
    private final Instant consensusTimestamp;

    public AccountExistenceValidator(final String name, final Instant consensusTimestamp) {
        this.name = name;
        this.consensusTimestamp = consensusTimestamp;
    }

    @Override
    public void validate(final List<RecordWithSidecars> recordFiles) {
        final var accountExists = new AtomicBoolean();
        streamOfItemsFrom(recordFiles)
                .filter(this::isAtConsensusTime)
                .forEach(item -> {
                    final var receipt = item.getRecord().getReceipt();
                    if (receipt.hasAccountID()) {
                        accountExists.set(true);
                    }
                });
        if (!accountExists.get()) {
            Assertions.fail("Expected '" + name
                    + "' to be created at " + consensusTimestamp
                    + ", but it was not");
        }
    }

    private boolean isAtConsensusTime(final RecordStreamItem item) {
        final var timestamp = item.getRecord().getConsensusTimestamp();
        final var timeHere = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        return timeHere.equals(consensusTimestamp);
    }
}
