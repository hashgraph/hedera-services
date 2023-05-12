/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is the confidential and proprietary information of
 * Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Hedera Hashgraph.
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

/**
 * This module exports collections specialized for use in the JasperDB
 * VirtualDataSource implementation. Except for the {@link com.swirlds.jasperdb.collections.ThreeLongsList},
 * the main design constraint is to maximize performance while preserving
 * safety given the concurrent usage patterns of JasperDB.
 *
 * Implementations of {@link com.swirlds.jasperdb.collections.HashList} and
 * {@link com.swirlds.jasperdb.collections.LongList} behave as simple maps
 * with {@code long} keys. The {@link com.swirlds.jasperdb.collections.ImmutableIndexedObjectList}
 * provides a copy-on-write list that maintains the self-reported order of
 * a collection of {@link com.swirlds.jasperdb.collections.IndexedObject}s.
 *
 * Since JasperDB typically only needs {@code long} keys in a contiguous
 * numeric range starting from some minimum value, there is also a theme of
 * reducing memory usage by not allocating storage for list prefixes.
 */
package com.swirlds.jasperdb.collections;
