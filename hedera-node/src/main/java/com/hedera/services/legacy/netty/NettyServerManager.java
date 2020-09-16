package com.hedera.services.legacy.netty;

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

import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.legacy.logic.ApplicationConstants;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.net.ssl.SSLException;

public class NettyServerManager {
	private static final Logger log = LogManager.getLogger(NettyServerManager.class);

	public NettyServerBuilder buildNettyServer(int port) throws FileNotFoundException, SSLException {
		return buildNettyServer(port, false);
	}

	public NettyServerBuilder buildNettyServer(int port, boolean tlsSupport) throws FileNotFoundException,
			SSLException {
		String nettyMode = PropertiesLoader.getNettyMode();
		boolean isDevMode = ApplicationConstants.NETTY_MODE_DEV.equalsIgnoreCase(nettyMode);

		NettyServerBuilder servBuilder = NettyServerBuilder.forPort(port);

		if (isDevMode) {
			log.info("NETTY SERVER in ROGUE MODE");
		} else {
			log.info("NETTY SERVER in DOMESTICATED MODE");

			//Assign values from propertyfile if exists
			long keepAliveTime = PropertiesLoader.getNettyKeepAliveTime();
			long keepAliveTimeout = PropertiesLoader.getNettyKeepAliveTimeOut();
			long maxConnectionAge = PropertiesLoader.getNettyMaxConnectionAge();
			long maxConnectionAgeGrace = PropertiesLoader.getNettyMaxConnectionAgeGrace();
			long maxConnectionIdle = PropertiesLoader.getNettyMaxConnectionIdle();
			int maxConcurrentCalls = PropertiesLoader.getNettyMaxConcurrentCalls();
			int nettyFlowControlWindow = PropertiesLoader.getNettyFlowControlWindow();

			log.info("Setting Netty Server Properties ");
			log.info("KeepAlive Time :" + keepAliveTime);
			log.info("KeepAliveTimeout :" + keepAliveTimeout);
			log.info("maxConnectionAge :" + maxConnectionAge);
			log.info("maxConnectionAgeGrace :" + maxConnectionAgeGrace);
			log.info("maxConnectionIdle :" + maxConnectionIdle);
			log.info("maxConcurrentCalls :" + maxConcurrentCalls);
			log.info("nettyFlowControlWindow :" + nettyFlowControlWindow);

			servBuilder = servBuilder
					.keepAliveTime(keepAliveTime, TimeUnit.SECONDS)
					.keepAliveTimeout(keepAliveTimeout, TimeUnit.SECONDS)
					.maxConnectionAge(maxConnectionAge, TimeUnit.SECONDS)
					.maxConnectionAgeGrace(maxConnectionAgeGrace, TimeUnit.SECONDS)
					.maxConnectionIdle(maxConnectionIdle, TimeUnit.SECONDS)
					.maxConcurrentCallsPerConnection(maxConcurrentCalls)
					.permitKeepAliveTime(keepAliveTime, TimeUnit.SECONDS)
					.directExecutor()
					.channelType(EpollServerSocketChannel.class)
					.bossEventLoopGroup(new EpollEventLoopGroup())
					.workerEventLoopGroup(new EpollEventLoopGroup())
					.flowControlWindow(nettyFlowControlWindow);
		}

		if (tlsSupport) {
			log.info("NETTY SERVER with TLS support on port " + port);
			File certChain = new File("hedera.crt");
			File privateKey = new File("hedera.key");
			if (!certChain.exists() || !privateKey.exists()) {
				throw new FileNotFoundException(
						"hedera.crt or hedera.key could not be found in Services app base folder");
			}
			String[] protocols = new String[] { "TLSv1.2", "TLSv1.3" };
			List<String> ciphers = Arrays.asList(
					"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
					"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"
			);
			SslContextBuilder contextBuilder =
					GrpcSslContexts.configure(SslContextBuilder.forServer(certChain, privateKey));
			contextBuilder
					.protocols(protocols)
					.ciphers(ciphers, SupportedCipherSuiteFilter.INSTANCE);
			servBuilder = servBuilder.sslContext(contextBuilder.build());
		}

		return servBuilder;
	}
}
