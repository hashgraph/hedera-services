package com.hedera.services.files.interceptors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.files.FileUpdateInterceptor;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.AbstractMap;
import java.util.Map;
import java.util.OptionalInt;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class FeeSchedulesManager implements FileUpdateInterceptor {
	public static final Logger log = LogManager.getLogger(FeeSchedulesManager.class);
	private static final int APPLICABLE_PRIORITY = 0;

	private final FeeCalculator fees;
	private final long fileNum;

	static final Map.Entry<ResponseCodeEnum, Boolean> YES_VERDICT =
			new AbstractMap.SimpleImmutableEntry<>(SUCCESS, true);
	static final Map.Entry<ResponseCodeEnum, Boolean> OK_FOR_NOW_VERDICT =
			new AbstractMap.SimpleImmutableEntry<>(FEE_SCHEDULE_FILE_PART_UPLOADED, true);

	public FeeSchedulesManager(
			FeeCalculator fees,
			PropertySource properties
	) {
		this.fees = fees;
		fileNum = properties.getLongProperty("files.feeSchedules.num");
	}

	@Override
	public OptionalInt priorityForCandidate(FileID id) {
		return (id.getFileNum() == fileNum) ? OptionalInt.of(APPLICABLE_PRIORITY) : OptionalInt.empty();
	}

	@Override
	public Map.Entry<ResponseCodeEnum, Boolean> preUpdate(FileID id, byte[] newContents) {
		if (priorityForCandidate(id).isPresent()) {
			return areValid(newContents) ? YES_VERDICT : OK_FOR_NOW_VERDICT;
		} else {
			return YES_VERDICT;
		}
	}

	@Override
	public void postUpdate(FileID id, byte[] contents) {
		if (priorityForCandidate(id).isPresent() && areValid(contents)) {
			fees.init();
		}
	}

	@Override
	public Map.Entry<ResponseCodeEnum, Boolean> preDelete(FileID id) {
		return YES_VERDICT;
	}

	@Override
	public Map.Entry<ResponseCodeEnum, Boolean> preAttrChange(FileID id, JFileInfo newAttr) {
		return YES_VERDICT;
	}

	private boolean areValid(byte[] contents) {
		try {
			log.info("Trying to parse fee schedules from {} bytes...", contents.length);
			CurrentAndNextFeeSchedule.parseFrom(contents);
			log.info("...successful, new schedules will be applied.");
			return true;
		} catch (InvalidProtocolBufferException ignore) {
			log.info("...unsuccessful, old schedules will remain active. {}", ignore.getMessage());
			return false;
		}
	}
}
