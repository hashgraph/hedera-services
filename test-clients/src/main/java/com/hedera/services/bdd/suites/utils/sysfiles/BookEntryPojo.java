package com.hedera.services.bdd.suites.utils.sysfiles;

/*-
 * ‌
 * Hedera Services Test Clients
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
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.yahcli.commands.files.SysFileUploadCommand;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import org.apache.commons.codec.binary.Hex;
import org.junit.Assert;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookEntryPojo {
	private static final String MISSING_CERT_HASH = "<N/A>";
	private static final String SENTINEL_REPLACEMENT_VALUE = "!";

	static class EndpointPojo {
		private String ipAddressV4;
		private Integer port;

		public String getIpAddressV4() {
			return ipAddressV4;
		}

		public void setIpAddressV4(String ipAddressV4) {
			this.ipAddressV4 = ipAddressV4;
		}

		public Integer getPort() {
			return port;
		}

		public void setPort(Integer port) {
			this.port = port;
		}

		static EndpointPojo fromGrpc(ServiceEndpoint proto) {
			var pojo = new EndpointPojo();
			pojo.setIpAddressV4(asReadableIp(proto.getIpAddressV4()));
			pojo.setPort(proto.getPort());
			Assert.assertNotEquals("A port is a positive integer!", 0, pojo.getPort().intValue());
			return pojo;
		}

		ServiceEndpoint toGrpc() {
			return ServiceEndpoint.newBuilder()
					.setIpAddressV4(asOctets(ipAddressV4))
					.setPort(port)
					.build();
		}
	}

	private String deprecatedIp;
	private String deprecatedMemo;
	private Integer deprecatedPortNo;

	private Long stake;
	private Long nodeId;
	private String certHash = MISSING_CERT_HASH;
	private String rsaPubKey;
	private String nodeAccount;
	private String description;
	private List<EndpointPojo> endpoints;

	static BookEntryPojo fromGrpc(NodeAddress address) {
		var entry = new BookEntryPojo();

		entry.deprecatedIp = address.getIpAddress().isEmpty() ? null : address.getIpAddress().toStringUtf8();
		entry.deprecatedPortNo = address.getPortno();
		if (entry.deprecatedPortNo == 0) {
			entry.deprecatedPortNo = null;
		}
		entry.deprecatedMemo = address.getMemo().isEmpty() ? null : address.getMemo().toStringUtf8();

		entry.rsaPubKey = address.getRSAPubKey().isEmpty() ? null : address.getRSAPubKey();
		entry.nodeId = address.getNodeId();
		if (address.hasNodeAccountId()) {
			entry.nodeAccount = HapiPropertySource.asAccountString(address.getNodeAccountId());
		} else {
			try {
				var memo = address.getMemo().toStringUtf8();
				HapiPropertySource.asAccount(memo);
				entry.nodeAccount = memo;
			} catch (Exception ignore) {
				entry.nodeAccount = null;
			}
		}
		entry.certHash = address.getNodeCertHash().isEmpty() ? MISSING_CERT_HASH : address.getNodeCertHash().toStringUtf8();
		mapEndpoints(address, entry);

		entry.description = address.getDescription().isEmpty() ? null : address.getDescription();
		entry.stake = address.getStake();
		if (entry.stake == 0) {
			entry.stake = null;
		}

		return entry;
	}

	public Stream<NodeAddress> toGrpcStream() {
		var grpc = NodeAddress.newBuilder();

		if (deprecatedIp != null) {
			grpc.setIpAddress(ByteString.copyFromUtf8(deprecatedIp));
		}
		grpc.setPortno(Optional.ofNullable(deprecatedPortNo).orElse(0));
		if (deprecatedMemo != null) {
			grpc.setMemo(ByteString.copyFromUtf8(deprecatedMemo));
		}
		grpc.setNodeId(Optional.ofNullable(nodeId).orElse(0L));

		if (rsaPubKey != null) {
			if (rsaPubKey.equals(SENTINEL_REPLACEMENT_VALUE)) {
				var baseDir = SysFileUploadCommand.activeSrcDir.get() + File.separator + "pubkeys";
				var computedKey = asHexEncodedDerPubKey(baseDir, grpc.getNodeId());
				grpc.setRSAPubKey(computedKey);
			} else {
				grpc.setRSAPubKey(rsaPubKey);
			}
		}
		if (nodeAccount != null) {
			grpc.setNodeAccountId(HapiPropertySource.asAccount(nodeAccount));
		}
		if (!certHash.equals(MISSING_CERT_HASH)) {
			if (certHash.equals(SENTINEL_REPLACEMENT_VALUE)) {
				var baseDir = SysFileUploadCommand.activeSrcDir.get() + File.separator + "certs";
				var computedHash = asHexEncodedSha384HashFor(baseDir, grpc.getNodeId());
				grpc.setNodeCertHash(ByteString.copyFromUtf8(computedHash));
			} else {
				grpc.setNodeCertHash(ByteString.copyFromUtf8(certHash));
			}
		}

		for (var endpoint : endpoints) {
			grpc.addServiceEndpoint(endpoint.toGrpc());
		}
		if (description != null) {
			grpc.setDescription(description);
		}

		return Stream.of(grpc.build());
	}

	private static void mapEndpoints(NodeAddress from, BookEntryPojo to) {
		to.endpoints = from.getServiceEndpointList().stream()
				.map(EndpointPojo::fromGrpc)
				.collect(Collectors.toList());
	}

	public String getDeprecatedIp() {
		return deprecatedIp;
	}

	public void setDeprecatedIp(String deprecatedIp) {
		this.deprecatedIp = deprecatedIp;
	}

	public String getDeprecatedMemo() {
		return deprecatedMemo;
	}

	public void setDeprecatedMemo(String deprecatedMemo) {
		this.deprecatedMemo = deprecatedMemo;
	}

	public String getNodeAccount() {
		return nodeAccount;
	}

	public void setNodeAccount(String nodeAccount) {
		this.nodeAccount = nodeAccount;
	}

	public String getRsaPubKey() {
		return rsaPubKey;
	}

	public void setRsaPubKey(String rsaPubKey) {
		this.rsaPubKey = rsaPubKey;
	}

	public String getCertHash() {
		return certHash;
	}

	public void setCertHash(String certHash) {
		this.certHash = certHash;
	}

	public Integer getDeprecatedPortNo() {
		return deprecatedPortNo;
	}

	public void setDeprecatedPortNo(Integer deprecatedPortNo) {
		this.deprecatedPortNo = deprecatedPortNo;
	}

	public Long getNodeId() {
		return nodeId;
	}

	public void setNodeId(Long nodeId) {
		this.nodeId = nodeId;
	}

	public List<EndpointPojo> getEndpoints() {
		return endpoints;
	}

	public void setEndpoints(List<EndpointPojo> endpoints) {
		this.endpoints = endpoints;
	}

	static String asHexEncodedSha384HashFor(String baseDir, long nodeId) {
		try {
			var crtBytes = Files.readAllBytes(Paths.get(baseDir, String.format("node%d.crt", nodeId)));
			var crtHash = CommonUtils.noThrowSha384HashOf(crtBytes);
			return Hex.encodeHexString(crtHash);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	static String asHexEncodedDerPubKey(String baseDir, long nodeId) {
		try {
			var pubKeyBytes = Files.readAllBytes(Paths.get(baseDir, String.format("node%d.der", nodeId)));
			return Hex.encodeHexString(pubKeyBytes);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static ByteString asOctets(String ipAddressV4) {
		byte[] octets = new byte[4];
		String[] literals = ipAddressV4.split("[.]");
		for (int i = 0; i < 4; i++) {
			octets[i] = (byte) Integer.parseInt(literals[i]);
		}
		return ByteString.copyFrom(octets);
	}

	private static String asReadableIp(ByteString octets) {
		byte[] raw = octets.toByteArray();
		Assert.assertEquals("An IP4v4 address should have four octets!", 4, raw.length);
		var sb = new StringBuilder();
		for (int i = 0; i < 4; i++) {
			sb.append("" + (0xff & raw[i]));
			if (i != 3) {
				sb.append(".");
			}
		}
		return sb.toString();
	}
}
