package com.hedera.services.schedule;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Provides ledger for scheduled entities.
 *
 * @author Daniel Ivanov
 */
public class ScheduleCreationResult {
	private final ResponseCodeEnum status;
	private final Optional<ScheduleID> created;

	public ScheduleCreationResult(
			ResponseCodeEnum status,
			Optional<ScheduleID> created
	) {
		this.status = status;
		this.created = created;
	}

	public static ScheduleCreationResult failure(ResponseCodeEnum type) {
		return new ScheduleCreationResult(type, Optional.empty());
	}

	public static ScheduleCreationResult success(ScheduleID created) {
		return new ScheduleCreationResult(OK, Optional.of(created));
	}

	public ResponseCodeEnum getStatus() {
		return status;
	}

	public Optional<ScheduleID> getCreated() {
		return created;
	}
}
