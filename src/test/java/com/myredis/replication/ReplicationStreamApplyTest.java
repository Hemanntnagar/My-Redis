package com.myredis.replication;

import com.myredis.command.CommandClientRequestProcessor;
import com.myredis.config.ServerConfiguration;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.protocol.Resp2Decoder;
import com.myredis.protocol.RespArray;
import com.myredis.protocol.RespBulkString;
import com.myredis.server.ServerBootstrap;
import com.myredis.server.ServerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplicationStreamApplyTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void replicationFrameAppliesWrite() throws Exception {
        ServerContext context =
                new ServerBootstrap()
                        .bootstrap(
                                ServerConfiguration.builder()
                                        .persistenceDirectory(tempDir.toString())
                                        .build());
        var apply = context.collaborators().replicationApplyDispatcher();
        byte[] encoded =
                ReplicaStreamSession.encodeCommandParts(
                        List.of(
                                "SET".getBytes(StandardCharsets.UTF_8),
                                "k".getBytes(StandardCharsets.UTF_8),
                                "v".getBytes(StandardCharsets.UTF_8)));
        byte[] wrapped = ReplicaStreamSession.wrapReplicationFrame(encoded);
        Resp2Decoder decoder = new Resp2Decoder();
        Object decoded = decoder.decodeNext(ByteBuffer.wrap(wrapped));
        assertTrue(decoded instanceof RespArray array);
        ClientReply reply =
                apply.dispatch(
                        new ClientSession() {
                            @Override
                            public String id() {
                                return "test";
                            }

                            @Override
                            public SocketChannel channel() {
                                throw new UnsupportedOperationException();
                            }
                        },
                        "SET",
                        List.of("k".getBytes(StandardCharsets.UTF_8), "v".getBytes(StandardCharsets.UTF_8)));
        assertTrue(reply instanceof ClientReply.SimpleString);
    }
}
