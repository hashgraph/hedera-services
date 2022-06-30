package com.hedera.services.context.init;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.ServicesState;
import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.files.FileUpdateInterceptor;
import com.hedera.services.files.HederaFs;
import com.hedera.services.stream.RecordStreamManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.function.Consumer;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;

@Singleton
public class StateInitializationFlow {
	private static final Logger log = LogManager.getLogger(StateInitializationFlow.class);

	private final HederaFs hfs;
	private final HederaNumbers hederaNums;
	private final RecordStreamManager recordStreamManager;
	private final MutableStateChildren workingState;
	private final Set<FileUpdateInterceptor> fileUpdateInterceptors;

	@Inject
	public StateInitializationFlow(
			final HederaFs hfs,
			final HederaNumbers hederaNums,
			final RecordStreamManager recordStreamManager,
			final MutableStateChildren workingState,
			final Set<FileUpdateInterceptor> fileUpdateInterceptors
	) {
		this.hfs = hfs;
		this.hederaNums = hederaNums;
		this.workingState = workingState;
		this.recordStreamManager = recordStreamManager;
		this.fileUpdateInterceptors = fileUpdateInterceptors;
	}

	public void runWith(ServicesState activeState) {
		staticNumbersHolder.accept(hederaNums);

		workingState.updateFrom(activeState);
		log.info("Context updated with working state");

		final var activeHash = activeState.runningHashLeaf().getRunningHash().getHash();
		recordStreamManager.setInitialHash(activeHash);
		log.info("Record running hash initialized");

		if (hfs.numRegisteredInterceptors() == 0) {
			fileUpdateInterceptors.forEach(hfs::register);
			log.info("Registered {} file update interceptors", fileUpdateInterceptors.size());
		}
	}

	private static Consumer<HederaNumbers> staticNumbersHolder = STATIC_PROPERTIES::setNumbersFrom;

	/* --- Only used by unit tests --- */
	static void setStaticNumbersHolder(Consumer<HederaNumbers> staticNumbersHolder) {
		StateInitializationFlow.staticNumbersHolder = staticNumbersHolder;
	}
}
