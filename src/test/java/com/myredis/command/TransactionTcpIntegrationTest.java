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

class TransactionTcpIntegrationTest {

    @Test
    void multiExecAppliesQueuedCommands() throws Exception {
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

            output.write(TcpRespSupport.respCommand("MULTI"));
            output.flush();
            assertEquals("+OK", TcpRespSupport.readLine(input));

            output.write(TcpRespSupport.respCommand("SET", "tx-key", "1"));
            output.flush();
            assertEquals("+QUEUED", TcpRespSupport.readLine(input));

            output.write(TcpRespSupport.respCommand("INCR", "tx-key"));
            output.flush();
            assertEquals("+QUEUED", TcpRespSupport.readLine(input));

            output.write(TcpRespSupport.respCommand("EXEC"));
            output.flush();
            assertEquals("*2", TcpRespSupport.readLine(input));
            assertEquals("+OK", TcpRespSupport.readLine(input));
            assertEquals(":2", TcpRespSupport.readLine(input));

            output.write(TcpRespSupport.respCommand("GET", "tx-key"));
            output.flush();
            assertEquals("$1", TcpRespSupport.readLine(input));
            assertEquals("2", TcpRespSupport.readLine(input));
        } finally {
            server.stop();
        }
    }

    @Test
    void execReportsCommandErrorsWithoutAbortingWholeBatch() throws Exception {
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

            output.write(TcpRespSupport.respCommand("MULTI"));
            output.flush();
            assertEquals("+OK", TcpRespSupport.readLine(input));

            output.write(TcpRespSupport.respCommand("SET", "bad-counter", "not-integer"));
            output.flush();
            assertEquals("+QUEUED", TcpRespSupport.readLine(input));

            output.write(TcpRespSupport.respCommand("INCR", "bad-counter"));
            output.flush();
            assertEquals("+QUEUED", TcpRespSupport.readLine(input));

            output.write(TcpRespSupport.respCommand("SET", "ok-key", "v"));
            output.flush();
            assertEquals("+QUEUED", TcpRespSupport.readLine(input));

            output.write(TcpRespSupport.respCommand("EXEC"));
            output.flush();
            assertEquals("*3", TcpRespSupport.readLine(input));
            assertEquals("+OK", TcpRespSupport.readLine(input));
            String error = TcpRespSupport.readLine(input);
            assertTrue(error.startsWith("-ERR value is not an integer or out of range"));
            assertEquals("+OK", TcpRespSupport.readLine(input));
        } finally {
            server.stop();
        }
    }

    @Test
    void discardClearsQueuedCommands() throws Exception {
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

            output.write(TcpRespSupport.respCommand("SET", "k", "before"));
            output.flush();
            assertEquals("+OK", TcpRespSupport.readLine(input));

            output.write(TcpRespSupport.respCommand("MULTI"));
            output.write(TcpRespSupport.respCommand("SET", "k", "after"));
            output.write(TcpRespSupport.respCommand("DISCARD"));
            output.flush();
            assertEquals("+OK", TcpRespSupport.readLine(input));
            assertEquals("+QUEUED", TcpRespSupport.readLine(input));
            assertEquals("+OK", TcpRespSupport.readLine(input));

            output.write(TcpRespSupport.respCommand("GET", "k"));
            output.flush();
            assertEquals("$6", TcpRespSupport.readLine(input));
            assertEquals("before", TcpRespSupport.readLine(input));
        } finally {
            server.stop();
        }
    }

    @Test
    void concurrentClientsUseIndependentTransactionQueues() throws Exception {
        int port = TcpRespSupport.freeLocalPort();
        TcpListenServer server =
                new TcpListenServer(
                        ServerConfiguration.builder().host("127.0.0.1").port(port).build(),
                        new StructuredServerLogger(),
                        CommandClientRequestProcessor.withBuiltinCommands(new InMemoryKeyValueStore()));
        server.start();

        CountDownLatch clientAReady = new CountDownLatch(1);
        CountDownLatch clientBDone = new CountDownLatch(1);

        Thread clientA =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try (Socket socket = new Socket()) {
                                        socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
                                        OutputStream output = socket.getOutputStream();
                                        InputStream input = socket.getInputStream();
                                        output.write(TcpRespSupport.respCommand("MULTI"));
                                        output.write(TcpRespSupport.respCommand("SET", "a", "1"));
                                        output.flush();
                                        assertEquals("+OK", TcpRespSupport.readLine(input));
                                        assertEquals("+QUEUED", TcpRespSupport.readLine(input));
                                        clientAReady.countDown();
                                        assertTrue(clientBDone.await(5, TimeUnit.SECONDS));
                                        output.write(TcpRespSupport.respCommand("EXEC"));
                                        output.flush();
                                        assertEquals("*1", TcpRespSupport.readLine(input));
                                        assertEquals("+OK", TcpRespSupport.readLine(input));
                                    } catch (Exception failure) {
                                        throw new AssertionError(failure);
                                    }
                                });

        Thread clientB =
                Thread.ofVirtual()
                        .start(
                                () -> {
                                    try (Socket socket = new Socket()) {
                                        socket.connect(new InetSocketAddress("127.0.0.1", port), 2_000);
                                        OutputStream output = socket.getOutputStream();
                                        InputStream input = socket.getInputStream();
                                        assertTrue(clientAReady.await(5, TimeUnit.SECONDS));
                                        output.write(TcpRespSupport.respCommand("MULTI"));
                                        output.write(TcpRespSupport.respCommand("SET", "b", "2"));
                                        output.write(TcpRespSupport.respCommand("EXEC"));
                                        output.flush();
                                        assertEquals("+OK", TcpRespSupport.readLine(input));
                                        assertEquals("+QUEUED", TcpRespSupport.readLine(input));
                                        assertEquals("*1", TcpRespSupport.readLine(input));
                                        assertEquals("+OK", TcpRespSupport.readLine(input));
                                    } catch (Exception failure) {
                                        throw new AssertionError(failure);
                                    } finally {
                                        clientBDone.countDown();
                                    }
                                });

        clientA.join(10_000);
        clientB.join(10_000);
        server.stop();
    }
}
