package com.myredis.network;

import com.myredis.exception.ErrorResponseMapper;
import com.myredis.exception.ProtocolException;
import com.myredis.logging.LogContext;
import com.myredis.logging.StructuredServerLogger;
import com.myredis.protocol.Resp2Decoder;
import com.myredis.protocol.Resp2Encoder;
import com.myredis.protocol.RespArray;
import com.myredis.protocol.RespBulkString;
import com.myredis.protocol.RespNullArray;
import com.myredis.protocol.RespNullBulk;
import com.myredis.protocol.RespValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Blocking read/write loop for one client; decodes RESP commands and delegates execution to
 * {@link ClientRequestProcessor}. Each instance runs on its own virtual thread.
 */
final class ClientConnectionHandler implements Runnable {

    private final ConnectedClientSession session;
    private final ClientConnectionManager connectionManager;
    private final StructuredServerLogger logger;
    private final BooleanSupplier serverRunning;
    private final ClientRequestProcessor requestProcessor;
    private final Resp2Decoder decoder;
    private final Resp2Encoder encoder;

    ClientConnectionHandler(
            ConnectedClientSession session,
            ClientConnectionManager connectionManager,
            StructuredServerLogger logger,
            BooleanSupplier serverRunning,
            ClientRequestProcessor requestProcessor) {
        this.session = Objects.requireNonNull(session, "session");
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.serverRunning = Objects.requireNonNull(serverRunning, "serverRunning");
        this.requestProcessor = Objects.requireNonNull(requestProcessor, "requestProcessor");
        this.decoder = new Resp2Decoder();
        this.encoder = new Resp2Encoder();
    }

    @Override
    public void run() {
        SocketChannel channel = session.channel();
        ClientConnectionState state = new ClientConnectionState();
        try {
            while (serverRunning.getAsBoolean() && channel.isOpen()) {
                state.prepareForRead();
                int read = channel.read(state.readBuffer());
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                session.recordActivity();
                state.flipReadBuffer();
                if (!drainDecodedRequests(channel, state)) {
                    break;
                }
                state.compactReadBuffer();
            }
        } catch (IOException failure) {
            if (serverRunning.getAsBoolean() && channel.isOpen()) {
                logger.logWarning(
                        "client_read_failed",
                        "client socket read failed",
                        LogContext.builder()
                                .put("session_id", session.id())
                                .put("remote", String.valueOf(session.remoteAddress()))
                                .put("detail", failure.getMessage())
                                .build());
            }
        } finally {
            connectionManager.remove(session);
            logger.logInfo(
                    "client_disconnected",
                    "client connection closed",
                    LogContext.builder()
                            .put("session_id", session.id())
                            .put("remote", String.valueOf(session.remoteAddress()))
                            .build());
        }
    }

    private boolean drainDecodedRequests(SocketChannel channel, ClientConnectionState state)
            throws IOException {
        ByteBuffer input = state.readBuffer();
        while (input.hasRemaining()) {
            int before = input.position();
            Object decoded;
            try {
                decoded = decoder.decodeNext(input);
            } catch (ProtocolException failure) {
                writeReply(channel, state, ClientReply.error(failure.clientMessage()));
                return false;
            }
            if (decoded == null) {
                input.position(before);
                return true;
            }
            ClientReply reply;
            try {
                reply = handleDecodedRequest(decoded);
            } catch (ProtocolException failure) {
                reply = ClientReply.error(failure.clientMessage());
            } catch (RuntimeException failure) {
                reply = ClientReply.error(ErrorResponseMapper.toClientErrorMessage(failure));
                logger.logFailure(
                        failure,
                        LogContext.builder()
                                .put("session_id", session.id())
                                .put("remote", String.valueOf(session.remoteAddress()))
                                .build());
            }
            writeReply(channel, state, reply);
        }
        return true;
    }

    private ClientReply handleDecodedRequest(Object decoded) throws ProtocolException {
        if (decoded instanceof RespNullArray) {
            throw new ProtocolException("expected command array");
        }
        if (!(decoded instanceof RespArray command)) {
            throw new ProtocolException("expected command array");
        }
        List<byte[]> bulkArguments = toCommandArguments(command);
        ClientReply reply = requestProcessor.process(session, bulkArguments);
        return reply == null ? ClientReply.error(ErrorResponseMapper.GENERIC_CLIENT_ERROR) : reply;
    }

    private static List<byte[]> toCommandArguments(RespArray command) throws ProtocolException {
        if (command.elements().isEmpty()) {
            throw new ProtocolException("empty command");
        }
        List<byte[]> parts = new ArrayList<>(command.elements().size());
        for (RespValue element : command.elements()) {
            switch (element) {
                case RespBulkString bulk -> parts.add(bulk.data());
                case RespNullBulk ignored -> parts.add(null);
                default -> throw new ProtocolException("command arguments must be bulk strings");
            }
        }
        return parts;
    }

    private void writeReply(SocketChannel channel, ClientConnectionState state, ClientReply reply)
            throws IOException {
        state.ensureWriteCapacity(requiredEncodeBytes(reply));
        ByteBuffer output = state.writeBuffer();
        output.clear();
        encodeReply(reply, output);
        output.flip();
        while (output.hasRemaining()) {
            int written = channel.write(output);
            if (written < 0) {
                return;
            }
        }
        session.recordActivity();
    }

    private static int requiredEncodeBytes(ClientReply reply) {
        return switch (reply) {
            case ClientReply.SimpleString simple -> simple.value().length() + 16;
            case ClientReply.Error error -> error.message().length() + 16;
            case ClientReply.BulkString bulk -> bulk.value().length + 32;
            case ClientReply.NullBulk ignored -> 16;
            case ClientReply.Integer integer -> 32;
        };
    }

    private void encodeReply(ClientReply reply, ByteBuffer output) {
        switch (reply) {
            case ClientReply.SimpleString simple -> encoder.encodeSimpleString(simple.value(), output);
            case ClientReply.Error error -> encoder.encodeError(error.message(), output);
            case ClientReply.BulkString bulk -> encoder.encodeBulkString(bulk.value(), output);
            case ClientReply.NullBulk ignored -> encoder.encodeNullBulk(output);
            case ClientReply.Integer integer -> encoder.encodeInteger(integer.value(), output);
        }
    }
}
