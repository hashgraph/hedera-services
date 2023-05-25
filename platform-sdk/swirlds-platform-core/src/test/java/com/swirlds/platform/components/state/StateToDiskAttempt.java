package com.swirlds.platform.components.state;

import com.swirlds.platform.state.signed.SignedState;

import java.nio.file.Path;

/**
 * A record of an attempt to write a signed state to disk.
 * @param signedState the signed state that was written
 * @param directory the directory where the signed state was written
 * @param success whether the write was successful
 */
public record StateToDiskAttempt(SignedState signedState, Path directory, boolean success) {
}
