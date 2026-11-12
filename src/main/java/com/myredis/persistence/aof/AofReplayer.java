package com.myredis.persistence.aof;

import com.myredis.command.CommandDispatcher;
import com.myredis.exception.CommandException;
import com.myredis.exception.PersistenceException;
import com.myredis.exception.ProtocolException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Replays decoded AOF command records against a dispatcher. */
public final class AofReplayer {

    private AofReplayer() {}

    public record ReplaySummary(int recordsApplied, int incompleteTrailingBytes) {}

    public static ReplaySummary replay(byte[] fileBytes, CommandDispatcher dispatcher) throws PersistenceException {
        AofRecordFramer.ReadResult read = AofRecordFramer.readAllRecords(fileBytes);
        int applied = 0;
        for (byte[] payload : read.commandPayloads()) {
            try {
                List<byte[]> parts = AofRespCommandCodec.decode(payload);
                String commandName = new String(parts.getFirst(), StandardCharsets.UTF_8);
                List<byte[]> arguments = parts.size() == 1 ? List.of() : parts.subList(1, parts.size());
                ClientReply reply = dispatcher.dispatch(new ReplaySession(), commandName, arguments);
                if (reply instanceof ClientReply.Error) {
                    throw new PersistenceException("ERR AOF replay failed on " + commandName);
                }
                applied++;
            } catch (ProtocolException | CommandException failure) {
                throw PersistenceException.wrap("ERR invalid AOF record", failure);
            }
        }
        return new ReplaySummary(applied, read.ignoredTrailingBytes());
    }

    private static final class ReplaySession implements ClientSession {

        @Override
        public String id() {
            return "aof-replay";
        }

        @Override
        public java.nio.channels.SocketChannel channel() {
            throw new UnsupportedOperationException();
        }
    }
}
