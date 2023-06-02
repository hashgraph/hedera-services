package com.swirlds.platform.test.consensus.framework.validation;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import com.swirlds.test.framework.config.TestConfigBuilder;
import org.junit.jupiter.api.Assertions;

import java.util.Map;
import java.util.stream.Collectors;

public class NoEventsLost {
	private static final ConsensusConfig CONFIG = new TestConfigBuilder().getOrCreateConfig().getConfigData(
			ConsensusConfig.class);

	/**
	 * Validates that all ancient events are either stale or consensus, but not both. Non-ancient events could be
	 * neither, so they are not checked.
	 */
	public static void validateNoEventsAreLost(final ConsensusOutput output, final ConsensusOutput ignored) {
		final Map<Hash, EventImpl> stale = output.getStaleEvents().stream().collect(Collectors.toMap(
				EventImpl::getBaseHash,
				e -> e
		));
		final Map<Hash, EventImpl> cons = output.getConsensusRounds().stream()
				.flatMap(r -> r.getConsensusEvents().stream())
				.collect(Collectors.toMap(
						EventImpl::getBaseHash,
						e -> e
				));
		if (output.getConsensusRounds().isEmpty()) {
			// no consensus reached, nothing to check
			return;
		}
		final long nonAncientGen = output
				.getConsensusRounds()
				.getLast()
				.getSnapshot()
				.getMinimumGenerationNonAncient(CONFIG.roundsNonAncient());

		for (final EventImpl event : output.getAddedEvents()) {
			if (event.getGeneration() >= nonAncientGen) {
				// non-ancient events are not checked
				continue;
			}
			if (stale.containsKey(event.getBaseHash()) == cons.containsKey(event.getBaseHash())) {
				Assertions.fail(String.format(
						"An ancient event should be either stale or consensus, but not both!\n" +
								"nonAncientGen=%d, Event %s, stale=%s, consensus=%s",
						nonAncientGen,
						event.toShortString(),
						stale.containsKey(event.getBaseHash()),
						cons.containsKey(event.getBaseHash())
				));
			}
		}
	}
}
