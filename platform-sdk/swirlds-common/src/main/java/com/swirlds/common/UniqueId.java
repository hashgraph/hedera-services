// SPDX-License-Identifier: Apache-2.0
package com.swirlds.common;

/**
 * Specifies that each instance has a unique identifier. Usually used for enums.
 */
public interface UniqueId {
    /**
     * @return the unique ID of this instance
     */
    int getId();
}
