/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.utils.forensics;

import com.hederahashgraph.api.proto.java.Transaction;

/**
 * Wraps two record stream entries that <i>should</i> share the same {@link Transaction} and
 * consensus time, but have different {@link com.hederahashgraph.api.proto.java.TransactionRecord}s.
 * Will generally be used to represent a single divergence between two nodes' record streams.
 *
 * @param firstEntry the first entry sharing the same transaction and consensus time
 * @param secondEntry the second entry sharing the transaction and consensus time
 */
public record DifferingEntries(RecordStreamEntry firstEntry, RecordStreamEntry secondEntry) {}
