/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.ledger;

import static com.hedera.services.ledger.SigImpactHistorian.ChangeStatus.CHANGED;
import static com.hedera.services.ledger.SigImpactHistorian.ChangeStatus.UNCHANGED;
import static com.hedera.services.ledger.SigImpactHistorian.ChangeStatus.UNKNOWN;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.expiry.MonotonicFullQueueExpiries;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Tracks changes to ledger entities and aliases that can impact signature validity over a trailing
 * window of length given by the global/dynamic property {@code ledger.changeHistorian.memorySecs}.
 * (The impact historian will return {@code UNKNOWN} if asked for the change status of an entity
 * outside its tracked window.)
 *
 * <p>For an account, contract, file, topic, or token an impact can be a creation, update, deletion,
 * or auto-removal. For a schedule, an impact can be creation, deletion, or auto-removal. And for an
 * alias, a impact can only be a creation or auto-removal. (Note that non-system deletion
 * <i>actually</i> only has a sig impact for contracts, given the details of the {@link
 * com.hedera.services.sigs.metadata.StateChildrenSigMetadataLookup} implementation; but for
 * consistency we treat it has an impactful change for all entity types, since the amortized
 * performance penalty is virtually zero, and it is quite possible we will want to extend this sig
 * impact historian to a generalized change historian in the future.)
 *
 * <p>We need to track these changes to safely re-use in {@code handleTransaction} the signatures
 * created during {@code expandSignatures}. The signatures are created in {@code expandSignatures}
 * by looking up keys from a signed state. If during {@code handleTransaction}, none of the entities
 * linked to the transaction have changed since the state was signed, then in particular none of the
 * keys have changed; and we can re-use the expanded signatures (including their asynchronously
 * computed verification status).
 *
 * <p>But if any of the entities <b>have</b> experienced a change with signature impact, we must
 * re-expand the signatures in {@code handleTransaction} to be sure we are up-to-date.
 */
@Singleton
public class SigImpactHistorian {
    private final GlobalDynamicProperties dynamicProperties;

    /* The current time used to mark a change; statuses are returned given strictly earlier changes in the window. */
    private Instant now;
    /* Null if the historian has observed at least a full window of consensus times, otherwise the first known time. */
    private Instant firstNow;
    /* Has the historian seen at least ledger.changeHistorian.memorySecs full seconds of consensus times? */
    private boolean fullWindowElapsed = false;

    private final Map<Long, Instant> entityChangeTimes = new HashMap<>();
    private final Map<ByteString, Instant> aliasChangeTimes = new HashMap<>();
    private final MonotonicFullQueueExpiries<Long> entityChangeExpiries =
            new MonotonicFullQueueExpiries<>();
    private final MonotonicFullQueueExpiries<ByteString> aliasChangeExpiries =
            new MonotonicFullQueueExpiries<>();

    public enum ChangeStatus {
        CHANGED,
        UNCHANGED,
        UNKNOWN
    }

    @Inject
    public SigImpactHistorian(final GlobalDynamicProperties dynamicProperties) {
        this.dynamicProperties = dynamicProperties;
    }

    /**
     * Marks the new consensus time at which changes may happen.
     *
     * @param now the new consensus time of any changes
     */
    public void setChangeTime(final Instant now) {
        this.now = now;

        if (!fullWindowElapsed) {
            manageFirstWindow(now);
        }
    }

    /** Expires any tracked changes that are no longer in the current window. */
    public void purge() {
        final var thisSecond = now.getEpochSecond();
        expire(thisSecond, aliasChangeTimes, aliasChangeExpiries);
        expire(thisSecond, entityChangeTimes, entityChangeExpiries);
    }

    /**
     * Returns the change status of an entity (by its number) since a given instant.
     *
     * <ol>
     *   <li>If the instant is outside this historian's tracking window, returns {@code UNKNOWN}.
     *   <li>If the entity has a tracked change at or after the given instant, returns {@code
     *       CHANGED}.
     *   <li>If the entity has no tracked changes since the given instant, returns {@code
     *       UNCHANGED}.
     * </ol>
     *
     * @param then the time after which changes matter
     * @param entityNum the number of the entity that may have changed
     * @return the status of changes to the entity since the given time
     */
    public ChangeStatus entityStatusSince(final Instant then, final long entityNum) {
        if (inFutureWindow(then)) {
            return UNKNOWN;
        }
        final var lastChangeInWindow = entityChangeTimes.get(entityNum);
        return statusGiven(lastChangeInWindow, then);
    }

    /**
     * Returns the change status of an alias since a given instant.
     *
     * <ol>
     *   <li>If the instant is outside this historian's tracking window, returns {@code UNKNOWN}.
     *   <li>If the alias has a tracked change at or after the given instant, returns {@code
     *       CHANGED}.
     *   <li>If the alias has no tracked changes since the given instant, returns {@code UNCHANGED}.
     * </ol>
     *
     * @param then the time after which changes matter
     * @param alias the alias that may have changed
     * @return the status of changes to the alias since the given time
     */
    public ChangeStatus aliasStatusSince(final Instant then, final ByteString alias) {
        if (inFutureWindow(then)) {
            return UNKNOWN;
        }
        final var lastChangeInWindow = aliasChangeTimes.get(alias);
        return statusGiven(lastChangeInWindow, then);
    }

    /**
     * Tracks the given entity (by number) as changed at the current time.
     *
     * @param entityNum the changed entity
     */
    public void markEntityChanged(final long entityNum) {
        requireNonNull(now, "Cannot mark an entity changed at null consensus time");
        entityChangeTimes.put(entityNum, now);
        entityChangeExpiries.track(entityNum, expirySec());
    }

    /**
     * Tracks the given alias as changed at the current time.
     *
     * @param alias the changed alias
     */
    public void markAliasChanged(final ByteString alias) {
        requireNonNull(now, "Cannot mark an entity changed at null consensus time");
        aliasChangeTimes.put(alias, now);
        aliasChangeExpiries.track(alias, expirySec());
    }

    /**
     * Invalidates all current history (important if the node fell behind and just reconnected).
     * Immediately following calls to {@code entityStatusSince()} and {@code aliasStatusSince()}
     * will return {@code UNKNOWN}.
     */
    public void invalidateCurrentWindow() {
        now = null;
        firstNow = null;
        fullWindowElapsed = false;

        aliasChangeTimes.clear();
        entityChangeTimes.clear();
        aliasChangeExpiries.reset();
        entityChangeExpiries.reset();
    }

    /* --- Internal helpers --- */
    private <T> void expire(
            final long thisSecond,
            final Map<T, Instant> changeTimes,
            final MonotonicFullQueueExpiries<T> expiries) {
        while (expiries.hasExpiringAt(thisSecond)) {
            final var maybeExpiredChange = expiries.expireNextAt(thisSecond);
            /* This could be null if two changes to the same thing happened in the same consensus second. */
            final var changeTime = changeTimes.get(maybeExpiredChange);
            if (changeTime != null && !inCurrentFullWindow(changeTime)) {
                changeTimes.remove(maybeExpiredChange);
            }
        }
    }

    private boolean inFutureWindow(final Instant then) {
        return now == null || !then.isBefore(now);
    }

    private void manageFirstWindow(final Instant now) {
        if (firstNow == null) {
            firstNow = now;
        } else {
            final var elapsedSecs = (int) (now.getEpochSecond() - firstNow.getEpochSecond());
            fullWindowElapsed = elapsedSecs > dynamicProperties.changeHistorianMemorySecs();
            if (fullWindowElapsed) {
                firstNow = null;
            }
        }
    }

    private ChangeStatus statusGiven(
            final @Nullable Instant lastChangeInWindow, final Instant then) {
        if (lastChangeInWindow == null) {
            if (fullWindowElapsed) {
                return inCurrentFullWindow(then) ? UNCHANGED : UNKNOWN;
            } else {
                return then.isAfter(firstNow) ? UNCHANGED : UNKNOWN;
            }
        } else {
            return then.isAfter(lastChangeInWindow) ? UNCHANGED : CHANGED;
        }
    }

    private boolean inCurrentFullWindow(final Instant then) {
        return then.getEpochSecond()
                >= now.getEpochSecond() - dynamicProperties.changeHistorianMemorySecs();
    }

    private long expirySec() {
        /* Remember each change for at least the requested memory seconds. */
        return now.getEpochSecond() + dynamicProperties.changeHistorianMemorySecs() + 1;
    }

    /* --- Only used for unit tests --- */
    Instant getNow() {
        return now;
    }

    Instant getFirstNow() {
        return firstNow;
    }

    boolean isFullWindowElapsed() {
        return fullWindowElapsed;
    }

    Map<Long, Instant> getEntityChangeTimes() {
        return entityChangeTimes;
    }

    Map<ByteString, Instant> getAliasChangeTimes() {
        return aliasChangeTimes;
    }

    MonotonicFullQueueExpiries<Long> getEntityChangeExpiries() {
        return entityChangeExpiries;
    }

    MonotonicFullQueueExpiries<ByteString> getAliasChangeExpiries() {
        return aliasChangeExpiries;
    }
}
