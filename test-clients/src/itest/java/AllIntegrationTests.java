import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * We first run sequentially a minimal set of {@link com.hedera.services.bdd.spec.HapiSpec}'s
 * that have "leaky" side effects like disabling a feature flag or setting restrictive
 * throttles.
 *
 * <p>These specs end by:
 * <ol>
 *      <li>Enabling all feature flags; and,</li>
 *      <li>Disabling contract throttles.</li>
 * </ol>
 *
 * <p>Afterwards we run concurrently a much larger set of non-interfering specs.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("java:S2699")
class AllIntegrationTests extends IntegrationTestBase {
    @Tag("integration")
    @Order(1)
    @TestFactory
    Collection<DynamicContainer> sequentialSpecsBySuite() {
        return Arrays.stream(SequentialSuites.all()).map(this::extractSpecsFromSuite).toList();
    }

    @Tag("integration")
    @Order(2)
    @TestFactory
    List<DynamicTest> concurrentSpecs() {
        return List.of(specsFrom(ConcurrentSuites.all()));
    }
}
