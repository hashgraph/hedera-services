package com.hedera.test.forensics.domain;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.hedera.services.state.submerkle.SolidityFnResult;
import org.apache.commons.codec.binary.Hex;

import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;

@JsonPropertyOrder({
		"id",
		"gas",
		"error",
		"result",
		"bloom",
		"logs"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PojoFunctionResult {
	private long gas;
	private String id;
	private String result;
	private String bloom;
	private String error;
	private List<String> creations = Collections.emptyList();
	private List<PojoFunctionLog> logs = Collections.EMPTY_LIST;;

	public static PojoFunctionResult from(SolidityFnResult value) {
		var pojo = new PojoFunctionResult();
		pojo.setId(PojoRecord.asString(value.getContractId()));
		pojo.setGas(value.getGasUsed());
		if (value.getResult() != null) {
			pojo.setResult(Hex.encodeHexString(value.getResult()));
		}
		if (value.getBloom() != null) {
			pojo.setBloom(Hex.encodeHexString(value.getBloom()));
		}
		if (value.getLogs() != null) {
			pojo.setLogs(value.getLogs().stream().map(PojoFunctionLog::from).collect(toList()));
		}
		if (value.getCreatedContractIds() != null) {
			pojo.setCreations(value.getCreatedContractIds()
					.stream()
					.map(jId -> String.format("%d.%d.%d", jId.shard(), jId.realm(), jId.num()))
					.collect(toList()));
		}
		pojo.setError(value.getError());
		return pojo;
	}

	public List<String> getCreations() {
		return creations;
	}

	public void setCreations(List<String> creations) {
		this.creations = creations;
	}

	public List<PojoFunctionLog> getLogs() {
		return logs;
	}

	public void setLogs(List<PojoFunctionLog> logs) {
		this.logs = logs;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getBloom() {
		return bloom;
	}

	public void setBloom(String bloom) {
		this.bloom = bloom;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public long getGas() {
		return gas;
	}

	public void setGas(long gas) {
		this.gas = gas;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
}
