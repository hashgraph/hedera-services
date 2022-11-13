package com.hedera.services.txns.validation;

import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import org.junit.jupiter.api.Test;

import static com.hedera.services.txns.validation.ExpiryMeta.UNUSED_FIELD_SENTINEL;
import static org.junit.jupiter.api.Assertions.*;

class ExpiryMetaTest {
    @Test
    void refusesToUpdateFileMetaIfUnsetPeriod() {
        final var subject = ExpiryMeta.withExplicitExpiry(bTime);
        final var target = new HFileMeta(false, someKey, aTime);
        assertThrows(IllegalStateException.class, () -> subject.updateFileMeta(target));
    }

    @Test
    void refusesToUpdateFileMetaIfUnsetExpiry() {
        final var subject = new ExpiryMeta(UNUSED_FIELD_SENTINEL, aPeriod, null);
        final var target = new HFileMeta(false, someKey, aTime);
        assertThrows(IllegalStateException.class, () -> subject.updateFileMeta(target));
    }

    @Test
    void nullsOutAutoRenewIfNull() {
        final var subject = new ExpiryMeta(bTime, 0, null);
        final var target = new HFileMeta(false, someKey, aTime);
        subject.updateFileMeta(target);
        assertEquals(bTime, target.getExpiry());
        assertEquals(0, target.getAutoRenewPeriod());
        assertNull(target.getAutoRenewId());
    }

    @Test
    void nullsOutAutoRenewIfMissingNum() {
        final var subject = new ExpiryMeta(bTime, 0, EntityNum.MISSING_NUM);
        final var target = new HFileMeta(false, someKey, aTime);
        target.setAutoRenewId(new EntityId(0, 0, 666));
        subject.updateFileMeta(target);
        assertEquals(bTime, target.getExpiry());
        assertEquals(0, target.getAutoRenewPeriod());
        assertNull(target.getAutoRenewId());
    }

    @Test
    void setsAutoRenewIfNotMissingNum() {
        final var newAutoRenewId = new EntityId(0, 0, 666);
        final var subject = new ExpiryMeta(bTime, 0, newAutoRenewId.asNum());
        final var target = new HFileMeta(false, someKey, aTime);
        subject.updateFileMeta(target);
        assertEquals(bTime, target.getExpiry());
        assertEquals(0, target.getAutoRenewPeriod());
        assertEquals(newAutoRenewId, target.getAutoRenewId());
    }

    @Test
    void setsExpiryIfPresentOnFileCreate() {
        final var op = FileCreateTransactionBody.newBuilder()
                .setExpirationTime(MiscUtils.asSecondsTimestamp(aTime))
                .build();

        final var expected = ExpiryMeta.withExplicitExpiry(aTime);
        final var actual = ExpiryMeta.fromFileCreateOp(op);

        assertEquals(expected, actual);
    }

    @Test
    void setsAutoRenewPeriodIfPresent() {
        final var op = FileCreateTransactionBody.newBuilder()
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(aPeriod).build())
                .build();

        final var expected = new ExpiryMeta(UNUSED_FIELD_SENTINEL, aPeriod, null);
        final var actual = ExpiryMeta.fromFileCreateOp(op);

        assertEquals(expected, actual);
    }

    @Test
    void setsAutoRenewNumIfPresent() {
        final var op = FileCreateTransactionBody.newBuilder()
                .setAutoRenewAccount(anAutoRenewNum.toGrpcAccountId())
                .build();

        final var expected = new ExpiryMeta(UNUSED_FIELD_SENTINEL, UNUSED_FIELD_SENTINEL, anAutoRenewNum);
        final var actual = ExpiryMeta.fromFileCreateOp(op);

        assertEquals(expected, actual);
    }

    private static final long aTime = 666_666_666L;
    private static final long bTime = 777_777_777L;
    private static final long aPeriod = 666_666L;
    private static final EntityNum anAutoRenewNum = EntityNum.fromLong(888);

    private static final JEd25519Key someKey = new JEd25519Key(";;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;".getBytes());
}