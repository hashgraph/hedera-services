package com.hedera.services.bdd.suites.contract.traceability;

import com.hedera.services.recordstreaming.RecordStreamingUtils;
import com.hedera.services.stream.proto.SidecarFile;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SidecarWatcher {

    public static final Pattern SIDECAR_FILE_REGEX =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}_\\d{2}_\\d{2}\\.\\d{9}Z_\\d{2}.rcd");
    private static final Logger log = LogManager.getLogger(SidecarWatcher.class);
    private final Queue<Pair<String, TransactionSidecarRecord>> expectedSidecars =
            new LinkedBlockingDeque<>();
    private final Map<String, List<Pair<TransactionSidecarRecord, TransactionSidecarRecord>>>
            failedSidecars = new HashMap<>();
    private boolean shouldTerminateAfterNext = false;

    public void startWatching(String s) throws IOException {
        final var watchService = FileSystems.getDefault().newWatchService();
        final var path = Paths.get(s);
        path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

        for (; ; ) {
            // wait for key to be signaled
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
                return;
            }

            for (final var event : key.pollEvents()) {
                final var kind = event.kind();

                // This key is registered only
                // for ENTRY_CREATE events,
                // but an OVERFLOW event can
                // occur regardless if events
                // are lost or discarded.
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                // The filename is the
                // context of the event.
                final var ev = (WatchEvent<Path>) event;
                final var filename = ev.context();
                final var child = path.resolve(filename);
                log.info("Record stream file created -> {} ", child.getFileName());
                final var newFilePath = child.toAbsolutePath().toString();
                if (SIDECAR_FILE_REGEX.matcher(newFilePath).find()) {
                    log.info("We have a new sidecar.");
                    final var pair = RecordStreamingUtils.readSidecarFile(newFilePath);
                    if (pair.isEmpty()) {
                        log.fatal(
                                "An invalid sidecar file was generated from the consensus node. This is very bad.");
                        return;
                    }
                    onNewSidecarFile(pair.get());
                    //                    sidecarConsumer.accept(pair.get());
                    if (shouldTerminateAfterNext) {
                        watchService.close();
                        return;
                    }
                }
            }
            // Reset the key -- this step is critical if you want to
            // receive further watch events.  If the key is no longer valid,
            // the directory is inaccessible so exit the loop.
            final var valid = key.reset();
            if (!valid) {
                log.fatal("Error occurred in WatchServiceAPI. Exiting now.");
                break;
            }
        }
    }

    private void onNewSidecarFile(final SidecarFile sidecarFile) {
        for (final var actualSidecar : sidecarFile.getSidecarRecordsList()) {
            final var expectedSidecarPair = expectedSidecars.poll();
            final var expectedSidecar = expectedSidecarPair.getValue();
            if ((actualSidecar.hasBytecode() || actualSidecar.hasStateChanges())
                    && !actualSidecar.equals(expectedSidecar)) {
                final var spec = expectedSidecarPair.getKey();
                final var list = failedSidecars.getOrDefault(spec, new ArrayList<>());
                list.add(Pair.of(expectedSidecar, actualSidecar));
                failedSidecars.put(spec, list);
            } else if (actualSidecar.hasActions()) {
                //                if (!expectedSidecar
                //                        .getConsensusTimestamp()
                //                        .equals(actualSidecar.getConsensusTimestamp())) {
                //                    areSidecarsValid = false;
                //                    throw new RuntimeException();
                //                }
            }
        }
    }

    public void finishWatchingAfterNextSidecar() {
        shouldTerminateAfterNext = true;
    }

    public void addExpectedSidecar(Pair<String, TransactionSidecarRecord> newExpectedSidecar) {
        this.expectedSidecars.add(newExpectedSidecar);
    }

    public boolean thereAreNoMismatchedSidecars() {
        return failedSidecars.isEmpty();
    }

    public String printErrors() {
        final var messageBuilder = new StringBuilder();
        messageBuilder.append("Mismatch(es) between actual/expected sidecars present: ");
        for (final var entry : failedSidecars.entrySet()) {
            final var faultySidecars = entry.getValue();
            messageBuilder
                    .append("\n\n")
                    .append(faultySidecars.size())
                    .append(" SIDECAR MISMATCH(ES) in SPEC {")
                    .append(entry.getKey())
                    .append("}:");
            int i = 1;
            for (final var pair : faultySidecars) {
                messageBuilder
                        .append("\n******FAILURE #")
                        .append(i++)
                        .append("******\n")
                        .append("***Expected sidecar***\n")
                        .append(pair.getLeft())
                        .append("***Actual sidecar***\n")
                        .append(pair.getRight());
            }
        }
        return messageBuilder.toString();
    }

    public boolean thereAreNoWaitingSidecars() {
        return expectedSidecars.isEmpty();
    }
}
