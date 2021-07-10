/*
 * (c) 2016-2021 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package com.hedera.services.state.merkle.v2_swirlds;

import com.swirlds.common.io.SelfSerializable;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A virtual key, specifically for use with the Virtual FCMap {@link VFCMap}. The indexes
 * used for looking up values are all stored on disk in order to support virtualization to
 * massive numbers of entities. This requires that any key used with the {@link VFCMap}
 * needs to be serializable. To improve performance, this interface exposes some methods that
 * avoid instance creation and serialization for normal key activities like equality.
 */
public interface VKey extends SelfSerializable {
    int hashCode(); // THIS IS MANDATORY
    void serialize(ByteBuffer buffer) throws IOException;
    void deserialize(ByteBuffer buffer, int version) throws IOException;
    boolean equals(ByteBuffer buffer, int version) throws IOException;
}
