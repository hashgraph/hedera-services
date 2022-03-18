package com.hedera.services.context.primitives;

import com.hedera.services.ServicesState;
import com.hedera.services.context.ImmutableStateChildren;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.exceptions.NoValidSignedStateException;
import com.hedera.services.state.migration.StateVersions;
import com.swirlds.common.AutoCloseableWrapper;
import com.swirlds.common.Platform;

import java.util.Optional;

/**
 * Factory that provides view of last completed signed state and its state children.
 * Fetches the latest signed state from platform for each call to get/update state children.
 */
public class SignedStateViewFactory {
	private final Platform platform;

	public SignedStateViewFactory(Platform platform) {
		this.platform = platform;
	}

	/**
	 * Try to update the mutable state children with the children from the signed state.
	 * Throws {@code NoValidSignedStateException} when the provided state is invalid.
	 *
	 * @param children
	 * 		mutable children provided
	 * @throws NoValidSignedStateException
	 */
	public void tryToUpdate(MutableStateChildren children) throws NoValidSignedStateException {
		final var state = getLatestSignedState();
		if (!hasValidSignedState(state)) {
			throw new NoValidSignedStateException("State children require an valid state to update");
		}
		children.updateFromSigned(state, state.getTimeOfLastHandledTxn());
	}

	/**
	 * Gets the immutable state children from the latest signedState from platform. Returns {@code Optional.empty()}
	 * when the provided state is invalid.
	 *
	 * @return immutable state children
	 */
	public Optional<ImmutableStateChildren> tryToGet() {
		final var state = getLatestSignedState();
		if (!hasValidSignedState(state)) {
			return Optional.empty();
		}
		return Optional.of(new ImmutableStateChildren(state));
	}

	/**
	 * Checks if the signedState provided is valid
	 *
	 * @param state
	 * @return true if it is a valid signedState
	 */
	boolean hasValidSignedState(final ServicesState state) {
		if (state == null) {
			return false;
		}
		/*
		  Since we can't get the enclosing platform SignedState, we don't know exactly when this state was signed. So
		  we just use, as a guaranteed lower bound, the consensus time of its last-handled transaction.
		 */
		final var latestSigningTime = state.getTimeOfLastHandledTxn();

		/*
		 1. latestSigningTime is null when no transactions have been handled; abort now to avoid downstream NPE.
		 2. Signed state version is not CURRENT_VERSION when we just upgraded and don't yet have a signed state from
		 the current version.
		 3. Signed state is not initialized when the aliases FCHashMap has not been rebuilt in an uninitialized state
		 */
		return latestSigningTime != null && state.getStateVersion() == StateVersions.CURRENT_VERSION && state.isInitialized();
	}

	/**
	 * Gets the last completed signed state from platform.
	 * <b>IMPORTANT:</b> This should be called everytime we try to get or update state children, to ensure we have the
	 * latest state.
	 *
	 * @return last completed signed state
	 */
	ServicesState getLatestSignedState() {
		try (final AutoCloseableWrapper<ServicesState> wrapper = platform.getLastCompleteSwirldState()) {
			final var signedState = wrapper.get();
			if (signedState == null) {
				return null;
			}
			return signedState;
		}
	}
}
