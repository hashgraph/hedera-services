package com.hedera.services.sysfiles.domain.throttling;

import com.hedera.services.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class HapiThrottleUtilsTest {
    @Test
    void factoryWorks() throws IOException {
        final var proto = TestUtils.protoDefs("bootstrap/throttles.json");

        final var bucketA = proto.getThrottleBuckets(0);

        final var fromResult = HapiThrottleUtils.hapiBucketFromProto(bucketA);
        final var fromToResult = HapiThrottleUtils.hapiBucketToProto(fromResult);
        assertEquals(bucketA, fromToResult);
    }
}