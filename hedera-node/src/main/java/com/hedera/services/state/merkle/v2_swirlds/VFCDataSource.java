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

import com.swirlds.common.crypto.Hash;

import java.io.IOException;

public interface VFCDataSource<K extends VKey, V extends VValue> {
    public final static int INVALID_PATH = -1;
    // TODO I don't really want the IOExceptions here. What can we do about it?
    Hash loadHash(long path) throws IOException;
    V loadLeafValue(long path) throws IOException;
    V loadLeafValue(K key) throws IOException;
    K loadLeafKey(long path) throws IOException;
    long loadLeafPath(K key) throws IOException;
    void saveInternal(long path, Hash hash) throws IOException; // if only hash is dirty. At the moment, internal nodes only. new internals too.
    void updateLeaf(long oldPath, long newPath, K key) throws IOException; // if only the path has changed ON A LEAF. No change in hash, value.
    void updateLeaf(long path, V value, Hash hash) throws IOException; // If value changed but not path
    void addLeaf(long path, K key, V value, Hash hash) throws IOException; // If leaf is new
    void close() throws IOException;
}