package com.hedera.services.state.expiry;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.removal.RemovalWork;
import com.hedera.services.state.expiry.renewal.RenewalWork;
import com.hedera.services.utils.EntityNum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

import static com.hedera.services.state.expiry.EntityProcessResult.NOTHING_TO_DO;
import static com.hedera.services.state.expiry.EntityProcessResult.STILL_MORE_TO_DO;

@Singleton
public class AutoExpiryCycle {
    private static final Logger log = LogManager.getLogger(AutoExpiryCycle.class);
    private final RenewalWork renewalWork;
    private final RemovalWork removalWork;
    private final ClassificationWork classifier;
    private Instant cycleTime = null;

    @Inject
    public AutoExpiryCycle(
            final ClassificationWork classifier,
            final RenewalWork renewalWork,
            final RemovalWork removalWork) {
        this.renewalWork = renewalWork;
        this.removalWork = removalWork;
        this.classifier = classifier;
    }

    public void beginCycle(final Instant currentConsTime) {
        warnIfInCycle();
        cycleTime = currentConsTime;
    }

    public void endCycle() {
        warnIfNotInCycle();
        cycleTime = null;
    }

    public EntityProcessResult process(final long literalNum) {
        warnIfNotInCycle();

        final var entityNum = EntityNum.fromLong(literalNum);
        final var classification = classifier.classify(entityNum, cycleTime);

        return switch (classification) {
            case COME_BACK_LATER -> STILL_MORE_TO_DO;
            // Removal work
            case DETACHED_ACCOUNT_GRACE_PERIOD_OVER -> removalWork.tryToRemoveAccount(entityNum);
            case DETACHED_CONTRACT_GRACE_PERIOD_OVER -> removalWork.tryToRemoveContract(entityNum);
            // Renewal work
            case EXPIRED_ACCOUNT_READY_TO_RENEW -> renewalWork.tryToRenewAccount(
                    entityNum, cycleTime);
            case EXPIRED_CONTRACT_READY_TO_RENEW -> renewalWork.tryToRenewContract(
                    entityNum, cycleTime);
            default -> NOTHING_TO_DO;
        };
    }

    private void warnIfNotInCycle() {
        if (cycleTime == null) {
            log.warn("Cycle ended, but did not have a start time");
        }
    }

    private void warnIfInCycle() {
        if (cycleTime != null) {
            log.warn("Cycle started, but had not ended from {}", cycleTime);
        }
    }

    @VisibleForTesting
    Instant getCycleTime() {
        return cycleTime;
    }
}
