package com.swirlds.platform.components.state;

import com.swirlds.platform.state.signed.SignedState;

import java.nio.file.Path;

public record StateToDiskAttempt(SignedState signedState, Path directory, boolean success) {
}
