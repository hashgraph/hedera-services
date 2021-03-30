package com.hedera.services.grpc;

import io.grpc.netty.NettyServerBuilder;

import javax.net.ssl.SSLException;
import java.io.FileNotFoundException;

public interface NettyBuilderFactory {
	NettyServerBuilder builderFor(int port, boolean tlsSupport) throws FileNotFoundException, SSLException;
}
