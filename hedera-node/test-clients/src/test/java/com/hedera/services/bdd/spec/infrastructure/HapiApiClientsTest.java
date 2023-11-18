/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.props.NodeConnectInfo;
import com.hederahashgraph.api.proto.java.*;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http2.*;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.SocketAddress;

public class HapiApiClientsTest {

    private static HapiApiClients apiClients;
    private static AccountID accountID;
    private static NodeConnectInfo node;

    private static Channel serverChannel;
    private static EventLoopGroup bossGroup;
    private static EventLoopGroup workerGroup;
    private static ExecutorService serverExecutor;
    private static CountDownLatch latch;

    @BeforeEach
    public void startServer() throws InterruptedException {
        latch = new CountDownLatch(1);
        int port = 50211;
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(() -> {
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new Http2ServerInitializer());

                serverChannel = b.bind(port)
                        .addListener(future -> {
                            if (future.isSuccess()) {
                                System.out.println("Server successfully started.");
                                latch.countDown();
                            } else {
                                System.err.println("Server failed to bind to port: " + port);
                            }
                        })
                        .syncUninterruptibly()
                        .channel();
                System.out.println("HTTP/2 (no SSL) server started at port " + port);
            } catch (Exception e) {
                System.err.println("Server failed to start: " + e.getMessage());
            }
        });

        // Wait for the server to be up before proceeding
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Server did not start within the expected time");
        }
    }

    @AfterEach
    public void stopServer() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        if (serverExecutor != null) {
            serverExecutor.shutdown();
        }
    }

    @BeforeAll
    public static void setup() {
        // Initialize HapiApiClients with mock nodes or test configuration
        HapiSpecSetup setup = createTestSetup();
        node = setup.nodes().get(0);
        accountID = node.getAccount();
        System.out.printf("node: %s | accountID: %s\n", node.uri(), accountID);
        apiClients = HapiApiClients.clientsFor(setup);
    }

    /**
     * This test simulates the GOAWAY from the server by closing the channel.
     */
    @Test
    public void testConnectionAfterGoAwayTest() {
        apiClients.setRetries(0);

        // Get a stub to simulate GOAWAY
        ManagedChannel channel = getChannelToTerminate(apiClients);
        simulateGoAwaySignal(channel);

        // Attempt to use a stub from the terminated channel
        try {
            final var builder = CryptoGetAccountBalanceQuery.newBuilder().setAccountID(accountID);
            final var query = Query.newBuilder().setCryptogetAccountBalance(builder).build();
            final var response = apiClients.getCryptoSvcStub(accountID, false).cryptoGetBalance(query);
            fail("Expected an exception due to GOAWAY signal but none was thrown.");
        } catch (StatusRuntimeException e) {
            // Assert that the correct exception is thrown
            assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
        }
    }

    /**
     * This test actually processes the GOAWAY from the server,
     * AND ensures that we get an exception when there are no retries enabled.
     */
    @Test
    public void testConnectionNormalBehaviorTest() {
        apiClients.setRetries(0);

        // Attempt to use a stub from the terminated channel
        try {
            final var builder = CryptoGetAccountBalanceQuery.newBuilder().setAccountID(accountID);
            final var query =
                    Query.newBuilder().setCryptogetAccountBalance(builder).build();
            final var client = apiClients.getCryptoSvcStub(accountID, false);
            System.out.println("using client to get the balance");
            final var response = client.cryptoGetBalance(query);
            System.out.printf("ran crypto get balance %s\n", response.toString());
            fail("Expected an exception due to GOAWAY signal but none was thrown.");
        } catch (StatusRuntimeException e) {
            System.out.printf("got an exception: %s\n", e);
            System.out.flush();
            // Assert that the correct exception is thrown
            assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
        }
    }

    /**
     * This test actually processes the GOAWAY from the server,
     * AND ensures that we pass when there are retries enabled.
     */
    @Test
    public void testConnectionRetryBehaviorTest() {
        // apiClients.setRetries(3);

        // Attempt to use a stub from the terminated channel
        try {
            final var builder = CryptoGetAccountBalanceQuery.newBuilder().setAccountID(accountID);
            final var query =
                    Query.newBuilder().setCryptogetAccountBalance(builder).build();
            final var client = apiClients.getCryptoSvcStub(accountID, false);
            System.out.println("using client to get the balance");
            final var response = client.cryptoGetBalance(query);
            System.out.printf("ran crypto get balance %s\n", response.toString());
            fail("Expected an exception due to GOAWAY signal but none was thrown.");
        } catch (StatusRuntimeException e) {
            System.out.printf("got an exception: %s\n", e);
            System.out.flush();
            // Assert that the correct exception is thrown
            assertEquals(Status.Code.UNAVAILABLE, e.getStatus().getCode());
        }
    }

    private static void simulateGoAwaySignal(ManagedChannel channel) {
        // Shutting down the channel to simulate a GOAWAY condition
        channel.shutdown();
        try {
            // Optionally, wait for the channel to terminate to ensure the GOAWAY condition is simulated.
            // I hate waiting like this, but I need to dig in more and create a hook for this.
            boolean terminated = channel.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                System.err.println("Channel did not terminate in the expected time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for channel termination", e);
        }
    }

    private static ManagedChannel getChannelToTerminate(HapiApiClients clients) {
        return clients.getChannel(accountID, false);
    }

    private static HapiSpecSetup createTestSetup() {
        // Create and return a HapiSpecSetup instance with the necessary test configuration
        //        accountID = AccountID.newBuilder().setAccountNum(FIRST_NODE_ACCOUNT_NUM).build();
        return new HapiSpecSetup(HapiSpecSetup.getDefaultPropertySource());
    }

    @AfterAll
    public static void tearDown() {
        // Clean up resources
        HapiApiClients.tearDown();
    }

    private static class Http2ServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline p = ch.pipeline();
            p.addLast(new LoggingHandler());
            p.addLast(Http2FrameCodecBuilder.forServer().build());
            p.addLast(new Http2ServerHandler());
        }
    }

    private static class Http2ServerHandler extends SimpleChannelInboundHandler<Http2Frame> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2Frame frame) {
            if (frame instanceof Http2HeadersFrame) {
                Http2HeadersFrame headersFrame = (Http2HeadersFrame) frame;

                // Send a Headers Frame as a response
                Http2Headers responseHeaders = new DefaultHttp2Headers();
                responseHeaders.status("200"); // OK Status
                responseHeaders.add("content-type", "application/grpc");
                ctx.write(new DefaultHttp2HeadersFrame(responseHeaders).stream(headersFrame.stream()));

                // Optionally, send a Data Frame with some content
                ByteBuf content = ctx.alloc().buffer();

                long balance = 1000; // Mock balance
                var response = CryptoGetAccountBalanceResponse.newBuilder()
                        .setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(ResponseCodeEnum.OK))
                        .setAccountID(accountID)
                        .setBalance(balance)
                        .build();
                byte[] responseBytes = response.toByteArray(); // Serialize the response

                // Write the gRPC frame header
                content.writeByte(0); // Compression flag (0 for uncompressed)
                content.writeInt(responseBytes.length); // Message length
                content.writeBytes(responseBytes); // Write the serialized bytes to the buffer

                ctx.write(new DefaultHttp2DataFrame(content, false).stream(headersFrame.stream())); // Set endOfStream to false

                ctx.flush(); // Flush the frames

                // Send a GOAWAY frame to indicate no more streams will be accepted after a delay.
                ctx.executor().schedule(() -> {
                    ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR));
                }, 100, TimeUnit.MILLISECONDS); // Delay of 100 milliseconds

                ctx.close();
            }
        }
    }

    private static class LoggingHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            SocketAddress remoteAddress = ctx.channel().remoteAddress();
            System.out.println("Connection established with: " + remoteAddress);
            super.channelActive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("Exception caught: " + cause.getMessage());
            ctx.close();
        }
    }
}
