/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.recovery.emergencyfile;

/**
 * The intervals at which various stream files are written, in milliseconds.
 * @param record record stream files are written at this interval, in milliseconds
 * @param event event stream files are written at this interval, in milliseconds
 * @param balances balance files are written at this interval, in milliseconds
 */
public record Intervals(long record, long event, long balances) {}
