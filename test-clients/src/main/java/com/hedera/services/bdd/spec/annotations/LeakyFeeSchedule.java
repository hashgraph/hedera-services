/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.annotations;

/**
 * Used to annotate a {@link com.hedera.services.bdd.spec.HapiSpec} that changes the fee schedule
 * system file {@code 0.0.111}. Because this file is larger than 6kb (the maximum size of a
 * transaction), it cannot be changed atomically. Instead, it is changed in parts, beginning with a
 * {@code fileUpdate} and followed by {@code fileAppend} operations.
 *
 * <p>This means that any concurrent spec that downloads the fee schedule file will see it in an
 * inconsistent state, and will fail because it cannot compute fees correctly. (For example, if the
 * file is 10kb, and only the first 6kb are updated, then the second 4kb will be missing and the
 * file will be invalid.)
 *
 * <p>Therefore, any spec that changes the fee schedule file should be annotated with this
 * annotation.
 */
public @interface LeakyFeeSchedule {}
