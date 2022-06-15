package com.hedera.services.bdd.junit;

import com.hedera.services.bdd.suites.HapiApiSuite;
import org.junit.jupiter.api.DynamicContainer;

import java.util.function.Supplier;

import static com.hedera.services.bdd.suites.HapiApiSuite.ETH_SUFFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Base class with some utility methods that can be used by JUnit-based test classes.
 */
public abstract class TestBase {
    /**
     * Utility that creates a DynamicTest for each HapiApiSpec in the given suite.
     *
     * @param suiteSupplier
     * @return
     */
    protected final DynamicContainer extractSpecsFromSuite(final Supplier<HapiApiSuite> suiteSupplier) {
        final var suite = suiteSupplier.get();
        final var tests = suite.getSpecsInSuite()
                .stream()
                .map(s -> dynamicTest(s.getName(), () -> {
                            s.run();
                            assertEquals(s.getExpectedFinalStatus(), s.getStatus(),
                                    "Failure in SUITE {" + suite.getClass().getSimpleName() + "}, while " +
                                            "executing " +
                                            "SPEC {" + s.getName() + "}");
                        }
                ));
        return dynamicContainer(suite.getClass().getSimpleName(), tests);
    }

    protected final DynamicContainer extractSpecsFromSuiteForEth(final Supplier<HapiApiSuite> suiteSupplier) {
        final var suite = suiteSupplier.get();
        final var tests = suite.getSpecsInSuite()
                .stream()
                .map(s -> dynamicTest(s.getName() + ETH_SUFFIX, () -> {
                            s.setSuitePrefix(suite.getClass().getSimpleName() + ETH_SUFFIX);
                            s.run();
                            assertEquals(s.getExpectedFinalStatus(), s.getStatus(),
                                    "\n\t\t\tFailure in SUITE {" + suite.getClass().getSimpleName() + ETH_SUFFIX + "}, " +
                                            "while " +
                                            "executing " +
                                            "SPEC {" + s.getName() + ETH_SUFFIX + "}");
                        }
                ));
        return dynamicContainer(suite.getClass().getSimpleName(), tests);
    }
}
