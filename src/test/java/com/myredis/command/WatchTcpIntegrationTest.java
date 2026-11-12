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

class WatchTcpIntegrationTest {

    @Test
    void execAbortsWhenWatchedKeyChanges() throws Exception {
        int port = TcpRespSupport.freeLocalPort();
        TcpListenServer server =
                new TcpListenServer(
                        ServerConfiguration.builder().host("127.0.0.1").port(port).build(),
                        new StructuredServerLogger(),
                        CommandClientRequestProcessor.withBuiltinCommands(new InMemoryKeyValueStore()));
        server.start();

        CountDownLatch watcherReady = new CountDownLatch(1);
        CountDownLatch mutationDone = new CountDownLatch(1);

        Thread watcher =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try (Socket socket = new Socket()) {
                                        socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
                                        OutputStream output = socket.getOutputStream();
                                        InputStream input = socket.getInputStream();

                                        output.write(TcpRespSupport.respCommand("SET", "balance", "10"));
                                        output.flush();
                                        assertEquals("+OK", TcpRespSupport.readLine(input));

                                        output.write(TcpRespSupport.respCommand("WATCH", "balance"));
                                        output.flush();
                                        assertEquals("+OK", TcpRespSupport.readLine(input));

                                        output.write(TcpRespSupport.respCommand("MULTI"));
                                        output.write(TcpRespSupport.respCommand("INCR", "balance"));
                                        output.flush();
                                        assertEquals("+OK", TcpRespSupport.readLine(input));
                                        assertEquals("+QUEUED", TcpRespSupport.readLine(input));
                                        watcherReady.countDown();
                                        assertTrue(mutationDone.await(5, TimeUnit.SECONDS));

                                        output.write(TcpRespSupport.respCommand("EXEC"));
                                        output.flush();
                                        assertEquals("$-1", TcpRespSupport.readLine(input));
                                    } catch (Exception failure) {
                                        throw new AssertionError(failure);
                                    }
                                });

        Thread mutator =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try (Socket socket = new Socket()) {
                                        socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
                                        OutputStream output = socket.getOutputStream();
                                        InputStream input = socket.getInputStream();
                                        assertTrue(watcherReady.await(5, TimeUnit.SECONDS));
                                        output.write(TcpRespSupport.respCommand("SET", "balance", "99"));
                                        output.flush();
                                        assertEquals("+OK", TcpRespSupport.readLine(input));
                                    } catch (Exception failure) {
                                        throw new AssertionError(failure);
                                    } finally {
                                        mutationDone.countDown();
                                    }
                                });

        watcher.join(10_000);
        mutator.join(10_000);
        server.stop();
    }

    @Test
    void unwatchAllowsExecAfterConcurrentMutation() throws Exception {
        int port = TcpRespSupport.freeLocalPort();
        TcpListenServer server =
                new TcpListenServer(
                        ServerConfiguration.builder().host("127.0.0.1").port(port).build(),
                        new StructuredServerLogger(),
                        CommandClientRequestProcessor.withBuiltinCommands(new InMemoryKeyValueStore()));
        server.start();

        try (Socket client = new Socket()) {
            client.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
            OutputStream output = client.getOutputStream();
            InputStream input = client.getInputStream();

            output.write(TcpRespSupport.respCommand("SET", "n", "0"));
            output.write(TcpRespSupport.respCommand("WATCH", "n"));
            output.write(TcpRespSupport.respCommand("UNWATCH"));
            output.write(TcpRespSupport.respCommand("MULTI"));
            output.write(TcpRespSupport.respCommand("INCR", "n"));
            output.write(TcpRespSupport.respCommand("EXEC"));
            output.flush();

            for (int i = 0; i < 5; i++) {
                String line = TcpRespSupport.readLine(input);
                assertTrue(line.startsWith("+") || line.startsWith("*") || line.startsWith(":"));
                if (line.equals("*1")) {
                    assertEquals(":1", TcpRespSupport.readLine(input));
                    break;
                }
            }
        } finally {
            server.stop();
        }
    }
}
