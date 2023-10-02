package com.swirlds.platform.event.stream;

import org.junit.jupiter.api.DisplayName;

@DisplayName("PreconsensusEventStreamValidation Test")
class PreconsensusEventStreamValidationTests {

    /* TODO
     * <li>that there exists at least one PCES file</li>
     * <li>that the generations stored by the PCES "cover" all states on disk</li>
     * <li>that the number of discontinuities in the stream do not exceed a specified maximum</li>
     * <li>files do not contain events with illegal generations</li>
     * <li>events are in topological order</li>
     * <li>events only show up once in a stream (violations are permitted when there are discontinuities)</li>
     * <li>checks performed by {@link PreconsensusEventFileReader#fileSanityChecks(
     *boolean, long, long, long, long, Instant, PreconsensusEventFile) fileSanityChecks()}</li>
     */
}
