package com.hedera.services.context.primitives;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.ServicesState;
import com.hedera.services.context.ImmutableStateChildren;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.StateChildren;
import com.hedera.services.exceptions.NoValidSignedStateException;
import com.hedera.services.state.migration.StateVersions;
import com.swirlds.common.AutoCloseableWrapper;
import com.swirlds.common.Platform;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Factory that provides view of last completed signed state and its state children.
 * Provides access to get/update to the latest signed state children from platform.
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
	public void tryToUpdateToLatestSignedChildren(MutableStateChildren children) throws NoValidSignedStateException {
		doWithLatest(state -> children.updateFromSigned(state, state.getTimeOfLastHandledTxn()));
	}

	/**
	 * Gets the immutable state children from the latest signedState from platform. Returns {@code Optional.empty()}
	 * when the provided state is invalid.
	 *
	 * @return latest state children from state
	 */
	public Optional<StateChildren> tryToGetLatestSignedChildren() {
		final var ref = new AtomicReference<StateChildren>();
		try {
			doWithLatest(state -> ref.set(new ImmutableStateChildren(state)));
			return Optional.of(ref.get());
		} catch (NoValidSignedStateException ignore) {
			return Optional.empty();
		}
	}

	/**
	 * Checks if the signedState provided is valid
	 *
	 * @param state
	 * 		provided services state
	 * @return true if it is a valid signedState
	 */
	boolean isValid(final ServicesState state) {
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
	 * Uses the last completed signed state from platform to get/update state children.
	 * <b>IMPORTANT:</b> This should be called everytime we try to get or update state children, to ensure we have the
	 * latest state.
	 *
	 * @param action
	 * 		consumer of services tate to perform get/update of sttae children
	 * @throws NoValidSignedStateException
	 */
	private void doWithLatest(final Consumer<ServicesState> action) throws NoValidSignedStateException {
		try (final AutoCloseableWrapper<ServicesState> wrapper = platform.getLastCompleteSwirldState()) {
			final var signedState = wrapper.get();
			if (!isValid(signedState)) {
				throw new NoValidSignedStateException("State children require an valid state to update");
			}
			action.accept(signedState);
		}
	}
}
