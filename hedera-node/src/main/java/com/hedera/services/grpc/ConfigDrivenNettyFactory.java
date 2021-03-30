package com.hedera.services.grpc;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.context.properties.Profile;
import io.grpc.netty.NettyServerBuilder;

import javax.net.ssl.SSLException;
import java.io.FileNotFoundException;
import java.util.concurrent.TimeUnit;

public class ConfigDrivenNettyFactory implements NettyBuilderFactory {
	private final NodeLocalProperties nodeProperties;

	public ConfigDrivenNettyFactory( NodeLocalProperties nodeProperties) {
		this.nodeProperties = nodeProperties;
	}

	@Override
	public NettyServerBuilder builderFor(int port, boolean tlsSupport) throws FileNotFoundException, SSLException {
		var builder = NettyServerBuilder.forPort(port);

		if (nodeProperties.activeProfile() == Profile.PROD) {
			builder.keepAliveTime(nodeProperties.nettyProdKeepAliveTime(), TimeUnit.SECONDS);
		}

		return builder;
	}
}
