package com.swirlds.platform.recovery.emergencyfile;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The top level of the emergency recovery YAML structure.
 *
 * @param state
 * 		information about the state written to disk
 * @param boostrap
 * 		information about the state used to bootstrap event recovery. Not written during normal
 * 		operation. Only written during event recovery.
 */
public record Recovery(State state, Boostrap boostrap, @JsonProperty("package") Package pkg) {
}
