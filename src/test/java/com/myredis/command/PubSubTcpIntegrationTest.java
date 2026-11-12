package com.myredis.command;

import com.myredis.config.ServerConfiguration;
import com.myredis.logging.StructuredServerLogger;
import com.myredis.network.TcpListenServer;
import com.myredis.storage.InMemoryKeyValueStore;
import com.myredis.support.TcpRespSupport;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PubSubTcpIntegrationTest {

    @Test
    void subscribePublishUnsubscribeAcrossClients() throws Exception {
        int port = TcpRespSupport.freeLocalPort();
        TcpListenServer server =
                new TcpListenServer(
                        ServerConfiguration.builder().host("127.0.0.1").port(port).build(),
                        new StructuredServerLogger(),
                        CommandClientRequestProcessor.withBuiltinCommands(new InMemoryKeyValueStore()));
        server.start();

        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch messageReceived = new CountDownLatch(1);

        Thread subscriber =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try (Socket socket = new Socket()) {
                                        socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
                                        OutputStream output = socket.getOutputStream();
                                        InputStream input = socket.getInputStream();

                                        output.write(TcpRespSupport.respCommand("SUBSCRIBE", "news"));
                                        output.flush();
                                        assertEquals("*3", TcpRespSupport.readLine(input));
                                        assertEquals("$9", TcpRespSupport.readLine(input));
                                        assertEquals("subscribe", TcpRespSupport.readLine(input));
                                        assertEquals("$4", TcpRespSupport.readLine(input));
                                        assertEquals("news", TcpRespSupport.readLine(input));
                                        assertEquals(":1", TcpRespSupport.readLine(input));
                                        subscribed.countDown();

                                        assertEquals("*3", TcpRespSupport.readLine(input));
                                        assertEquals("$7", TcpRespSupport.readLine(input));
                                        assertEquals("message", TcpRespSupport.readLine(input));
                                        assertEquals("$4", TcpRespSupport.readLine(input));
                                        assertEquals("news", TcpRespSupport.readLine(input));
                                        assertEquals("$5", TcpRespSupport.readLine(input));
                                        assertEquals("hello", TcpRespSupport.readLine(input));
                                        messageReceived.countDown();
                                    } catch (Exception failure) {
                                        throw new AssertionError(failure);
                                    }
                                });

        try (Socket publisher = new Socket()) {
            publisher.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            OutputStream output = publisher.getOutputStream();
            InputStream input = publisher.getInputStream();

            assertTrue(subscribed.await(5, TimeUnit.SECONDS));
            output.write(TcpRespSupport.respCommand("PUBLISH", "news", "hello"));
            output.flush();
            assertEquals(":1", TcpRespSupport.readLine(input));
        }

        assertTrue(messageReceived.await(5, TimeUnit.SECONDS));

        try (Socket unsubscriber = new Socket()) {
            unsubscriber.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            OutputStream output = unsubscriber.getOutputStream();
            InputStream input = unsubscriber.getInputStream();

            output.write(TcpRespSupport.respCommand("SUBSCRIBE", "news"));
            output.flush();
            assertEquals("*3", TcpRespSupport.readLine(input));
            assertEquals("$9", TcpRespSupport.readLine(input));
            assertEquals("subscribe", TcpRespSupport.readLine(input));
            assertEquals("$4", TcpRespSupport.readLine(input));
            assertEquals("news", TcpRespSupport.readLine(input));
            assertEquals(":1", TcpRespSupport.readLine(input));

            output.write(TcpRespSupport.respCommand("UNSUBSCRIBE", "news"));
            output.flush();
            assertEquals("*3", TcpRespSupport.readLine(input));
            assertEquals("$11", TcpRespSupport.readLine(input));
            assertEquals("unsubscribe", TcpRespSupport.readLine(input));
            assertEquals("$4", TcpRespSupport.readLine(input));
            assertEquals("news", TcpRespSupport.readLine(input));
            assertEquals(":0", TcpRespSupport.readLine(input));
        }

        subscriber.join(10_000);
        server.stop();
    }

    @Test
    void multipleSubscribersReceivePublish() throws Exception {
        int port = TcpRespSupport.freeLocalPort();
        TcpListenServer server =
                new TcpListenServer(
                        ServerConfiguration.builder().host("127.0.0.1").port(port).build(),
                        new StructuredServerLogger(),
                        CommandClientRequestProcessor.withBuiltinCommands(new InMemoryKeyValueStore()));
        server.start();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch received = new CountDownLatch(2);

        Runnable subscriberTask =
                () -> {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
                        OutputStream output = socket.getOutputStream();
                        InputStream input = socket.getInputStream();
                        output.write(TcpRespSupport.respCommand("SUBSCRIBE", "updates"));
                        output.flush();
                        for (int i = 0; i < 6; i++) {
                            TcpRespSupport.readLine(input);
                        }
                        ready.countDown();
                        for (int i = 0; i < 7; i++) {
                            TcpRespSupport.readLine(input);
                        }
                        received.countDown();
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                };

        Thread subA = Thread.ofVirtual().start(subscriberTask);
        Thread subB = Thread.ofVirtual().start(subscriberTask);
        assertTrue(ready.await(5, TimeUnit.SECONDS));

        try (Socket publisher = new Socket()) {
            publisher.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            OutputStream output = publisher.getOutputStream();
            InputStream input = publisher.getInputStream();
            output.write(TcpRespSupport.respCommand("PUBLISH", "updates", "ping"));
            output.flush();
            assertEquals(":2", TcpRespSupport.readLine(input));
        }

        assertTrue(received.await(5, TimeUnit.SECONDS));
        subA.join(10_000);
        subB.join(10_000);
        server.stop();
    }
}
