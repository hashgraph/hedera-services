package com.hedera.services.bdd.spec.assertions.matchers;

import com.hedera.services.bdd.suites.contract.traceability.TraceabilitySuite;
import org.testcontainers.shaded.org.hamcrest.Description;
import org.testcontainers.shaded.org.hamcrest.TypeSafeMatcher;

/**
 * Used in assertions to check if the gas is within 32 units of the expected gas.
 *
 * <p>
 * Depending on the addresses used in {@link TraceabilitySuite}, the hard-coded gas values may vary
 * slightly from the observed results. For example, the actual sidecar may have an intrinsic gas
 * cost differing from that of the expected sidecar by a value of {@code 12 * X}, where {@code X} is the
 * difference in the number of zero bytes in the transaction payload used between the actual
 * and hard-coded transactions (because the payload includes addresses with different numbers
 * of zeros in their hex encoding). So we allow for a variation of up to {@code 32L} gas between expected and actual.
 * </p>
 *
 * @author vyanev
 */
public class GasMatcher extends TypeSafeMatcher<Long> {

    private final Long expectedGas;
    private final boolean ignoreGas;

    public GasMatcher(Long expectedGas, boolean ignoreGas) {
        this.expectedGas = expectedGas;
        this.ignoreGas = ignoreGas;
    }

    /**
     * @param actualGas the actual gas
     * @return {@code true} if the actual gas is within 32 units of the expected gas
     */
    @Override
    public boolean matchesSafely(Long actualGas) {
        if (ignoreGas) {
            return true;
        }
        long delta = Math.max(0L, Math.abs(actualGas - expectedGas));
        return delta <= 32L;
    }

    /**
     * @param description {@link Description} of the expected gas
     */
    @Override
    public void describeTo(Description description) {
        if (ignoreGas) {
            description.appendText("any gas");
        } else {
            description.appendText("within 32 units of ").appendValue(expectedGas);
        }
    }
}