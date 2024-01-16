/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
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
 */

package com.swirlds.logging.api.internal.level;

/**
 * Enumeration representing marker decision.
 * <p>
 * This enumeration defines different options for specifying the state of a marker. It allows you to explicitly indicate whether
 * a marker is enabled, disabled, or should inherit its state from the package configuration.
 * <p>
 * The {@code ENABLED} option signifies that the marker is explicitly enabled.
 * The {@code DISABLED} option indicates that the marker is explicitly disabled.
 * The {@code UNDEFINED} option implies that the marker state should be inherited from the package configuration.
 */
public enum MarkerState {
    ENABLED,
    DISABLED,
    UNDEFINED;
}
