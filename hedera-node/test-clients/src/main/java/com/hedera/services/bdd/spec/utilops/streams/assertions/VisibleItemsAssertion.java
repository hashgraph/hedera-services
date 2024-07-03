package com.hedera.services.bdd.spec.utilops.streams.assertions;

import com.hedera.node.app.hapi.utils.forensics.RecordStreamEntry;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.stream.proto.RecordStreamItem;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.bdd.spec.utilops.streams.assertions.BaseIdScreenedAssertion.baseFieldsMatch;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class VisibleItemsAssertion implements RecordStreamAssertion {
    static final Logger log = LogManager.getLogger(VisibleItemsAssertion.class);

    private final HapiSpec spec;
    private final Set<String> unseenIds;
    private final CountDownLatch latch;
    private final List<RecordStreamEntry> entries = new ArrayList<>();

    @Nullable
    private String lastSeenId = null;

    public VisibleItemsAssertion(@NonNull final HapiSpec spec, @NonNull final String... specTxnIds) {
        this.spec = requireNonNull(spec);
        unseenIds = new HashSet<>() {{
            addAll(List.of(specTxnIds));
        }};
        latch = new CountDownLatch(unseenIds.size());
    }

    public CompletableFuture<List<RecordStreamEntry>> entriesWithin(@NonNull final Duration timeout) {
        requireNonNull(timeout);
        return CompletableFuture.supplyAsync(() -> {
            try {
                latch.await(timeout.toMillis(), MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for all expected items!");
            }
            entries.sort(Comparator.naturalOrder());
            return entries;
        });
    }

    @Override
    public boolean isApplicableTo(@NonNull final RecordStreamItem item) {
        unseenIds.stream()
                .filter(id -> spec.registry().getMaybeTxnId(id)
                        .filter(txnId -> baseFieldsMatch(txnId, item.getRecord().getTransactionID()))
                        .isPresent())
                .findFirst().ifPresentOrElse(seenId -> {
                    log.info("Saw {}!", seenId);
                    entries.add(RecordStreamEntry.from(item));
                    if (!seenId.equals(lastSeenId)) {
                        maybeFinishLastSeen();
                    }
                    lastSeenId = seenId;
                }, this::maybeFinishLastSeen);
        return true;
    }

    @Override
    public boolean test(@NonNull final RecordStreamItem item) throws AssertionError {
        return unseenIds.isEmpty();
    }

    private void maybeFinishLastSeen() {
        if (lastSeenId != null) {
            unseenIds.remove(lastSeenId);
            lastSeenId = null;
            latch.countDown();
        }
    }
}
