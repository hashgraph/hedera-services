package com.hedera.services.context.primitives;

import com.hedera.services.ServicesState;
import com.hedera.services.context.ImmutableStateChildren;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.state.migration.StateVersions;
import com.swirlds.common.AutoCloseableWrapper;
import com.swirlds.common.Platform;

import java.time.Instant;

/**
 * Factory that provides view of last completed signed state and its state children.
 * It also provides access to update/get the state children
 */
public class SignedStateViewFactory {
	private final Platform platform;
	private ServicesState signedState;
	private Instant latestSigningTime;

	public SignedStateViewFactory(Platform platform) {
		this.platform = platform;
		signedState = getSignedState();
	}

	/**
	 * Checks if the signedState provided is valid
	 *
	 * @return true if it is a valid signedState
	 */
	public boolean hasValidSignedState() {
		if (signedState == null) {
			return false;
		}
		/*
		  Since we can't get the enclosing platform SignedState, we don't know exactly when this state was signed. So
		  we just use, as a guaranteed lower bound, the consensus time of its last-handled transaction.
		 */
		latestSigningTime = signedState.getTimeOfLastHandledTxn();

		/*
		 1. latestSigningTime is null when no transactions have been handled; abort now to avoid downstream NPE.
		 2. Signed state version is not CURRENT_VERSION when we just upgraded and don't yet have a signed state from
		 the current version.
		 3. Signed state is not initialized when the aliases FCHashMap has not been rebuilt in an uninitialized state
		 */
		return latestSigningTime != null && signedState.getStateVersion() == StateVersions.CURRENT_VERSION && signedState.isInitialized();
	}

	/**
	 * Try to update the mutable state children with the children from the signed state
	 *
	 * @param children
	 * 		mutable children provided
	 * @return true if success, false otherwise
	 */
	public boolean tryToUpdate(MutableStateChildren children) {
		if (!hasValidSignedState()) {
			return false;
		}
		children.updateFromSigned(signedState, latestSigningTime);
		return true;
	}

	/**
	 * Gets the state children from the latest signedState from platform.
	 *
	 * @return immutable state children
	 */
	public ImmutableStateChildren tryToGet() {
		if (!hasValidSignedState()) {
			throw new IllegalArgumentException("State children require an valid state to update");
		}
		// Convenience wrapper for the latest state children received from Platform#getLastCompleteSwirldState()
		final ImmutableStateChildren signedChildren = new ImmutableStateChildren(signedState);
		signedChildren.updatePrimitiveChildren(signedState.getTimeOfLastHandledTxn());
		return signedChildren;
	}

	/**
	 * Gets the last completed signed state from platform
	 *
	 * @return last completed signed state
	 */
	ServicesState getSignedState() {
		try (final AutoCloseableWrapper<ServicesState> wrapper = platform.getLastCompleteSwirldState()) {
			signedState = wrapper.get();
			if (signedState == null) {
				return null;
			}
			return signedState;
		}
	}

	/* --- only for unit tests ---*/
	public Instant getLatestSigningTime() {
		return latestSigningTime;
	}
}
