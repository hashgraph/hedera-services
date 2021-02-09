package com.hedera.test.forensics.domain;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.hedera.services.state.submerkle.SolidityLog;
import org.apache.commons.codec.binary.Hex;

import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;

@JsonPropertyOrder({
		"id",
		"data",
		"bloom",
		"topics"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PojoFunctionLog {
	private String id;
	private String data;
	private String bloom;
	private List<String> topics = Collections.EMPTY_LIST;

	public static PojoFunctionLog from(SolidityLog value) {
		var pojo = new PojoFunctionLog();
		pojo.setId(PojoRecord.asString(value.getContractId()));
		if (value.getData() != null) {
			pojo.setData(Hex.encodeHexString(value.getData()));
		}
		if (value.getBloom() != null) {
			pojo.setBloom(Hex.encodeHexString(value.getBloom()));
		}
		if (value.getTopics() != null) {
			pojo.setTopics(value.getTopics().stream().map(Hex::encodeHexString).collect(toList()));
		}
		return pojo;
	}

	public List<String> getTopics() {
		return topics;
	}

	public void setTopics(List<String> topics) {
		this.topics = topics;
	}

	public String getBloom() {
		return bloom;
	}

	public void setBloom(String bloom) {
		this.bloom = bloom;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
}
