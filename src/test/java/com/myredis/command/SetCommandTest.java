package com.myredis.command;

import com.myredis.datatype.StringRedisValue;
import com.myredis.exception.CommandException;
import com.myredis.exception.StorageException;
import com.myredis.network.ClientReply;
import com.myredis.network.ClientSession;
import com.myredis.storage.InMemoryKeyValueStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SetCommandTest {

    private static final byte[] KEY_A = "set:a".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_B = "set:b".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY_C = "set:c".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MISSING = "set:missing".getBytes(StandardCharsets.UTF_8);

    private InMemoryKeyValueStore store;
    private SAddCommand sAddCommand;
    private SRemCommand sRemCommand;
    private SMembersCommand sMembersCommand;
    private SIsMemberCommand sIsMemberCommand;
    private SCardCommand sCardCommand;
    private SInterCommand sInterCommand;
    private SUnionCommand sUnionCommand;
    private SDiffCommand sDiffCommand;
    private TypeCommand typeCommand;
    private ClientSession session;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        sAddCommand = new SAddCommand(store);
        sRemCommand = new SRemCommand(store);
        sMembersCommand = new SMembersCommand(store);
        sIsMemberCommand = new SIsMemberCommand(store);
        sCardCommand = new SCardCommand(store);
        sInterCommand = new SInterCommand(store);
        sUnionCommand = new SUnionCommand(store);
        sDiffCommand = new SDiffCommand(store);
        typeCommand = new TypeCommand(store);
        session = new StubSession();
    }

    @Test
    void saddEnforcesUniquenessAndReturnsNewMemberCount() throws CommandException {
        assertEquals(2L, integer(sAddCommand.execute(session, List.of(KEY_A, mem("x"), mem("y")))));
        assertEquals(0L, integer(sAddCommand.execute(session, List.of(KEY_A, mem("x")))));
        assertEquals(1L, integer(sAddCommand.execute(session, List.of(KEY_A, mem("x"), mem("z")))));
        assertEquals(3L, integer(sCardCommand.execute(session, List.of(KEY_A))));
    }

    @Test
    void sremReturnsRemovedCountAndIgnoresMissingMembers() throws CommandException {
        sAddCommand.execute(session, List.of(KEY_A, mem("a"), mem("b"), mem("c")));
        assertEquals(2L, integer(sRemCommand.execute(session, List.of(KEY_A, mem("a"), mem("z"), mem("b")))));
        assertEquals(0L, integer(sRemCommand.execute(session, List.of(MISSING, mem("a")))));
        assertEquals(1L, integer(sCardCommand.execute(session, List.of(KEY_A))));
    }

    @Test
    void smembersAndSisMemberHandleMissingAndPresentKeys() throws CommandException {
        assertEquals(Set.of(), decodeSet(sMembersCommand.execute(session, List.of(MISSING))));
        assertEquals(0L, integer(sIsMemberCommand.execute(session, List.of(MISSING, mem("a")))));

        sAddCommand.execute(session, List.of(KEY_A, mem("a"), mem("b")));
        assertEquals(Set.of("a", "b"), decodeSet(sMembersCommand.execute(session, List.of(KEY_A))));
        assertEquals(1L, integer(sIsMemberCommand.execute(session, List.of(KEY_A, mem("b")))));
        assertEquals(0L, integer(sIsMemberCommand.execute(session, List.of(KEY_A, mem("c")))));
    }

    @Test
    void setAlgebraTreatsMissingKeysAsEmpty() throws CommandException {
        sAddCommand.execute(session, List.of(KEY_A, mem("a"), mem("b"), mem("c")));
        sAddCommand.execute(session, List.of(KEY_B, mem("b"), mem("c"), mem("d")));

        assertEquals(Set.of("b", "c"), decodeSet(sInterCommand.execute(session, List.of(KEY_A, KEY_B))));
        assertEquals(Set.of(), decodeSet(sInterCommand.execute(session, List.of(KEY_A, KEY_B, MISSING))));
        assertEquals(Set.of("a", "b", "c", "d"), decodeSet(sUnionCommand.execute(session, List.of(KEY_A, KEY_B, MISSING))));
        assertEquals(Set.of("a"), decodeSet(sDiffCommand.execute(session, List.of(KEY_A, KEY_B))));
        assertEquals(Set.of(), decodeSet(sDiffCommand.execute(session, List.of(MISSING, KEY_A))));
    }

    @Test
    void sdiffSubtractsUnionOfRemainingKeys() throws CommandException {
        sAddCommand.execute(session, List.of(KEY_A, mem("1"), mem("2"), mem("3")));
        sAddCommand.execute(session, List.of(KEY_B, mem("2")));
        sAddCommand.execute(session, List.of(KEY_C, mem("3"), mem("4")));
        assertEquals(Set.of("1"), decodeSet(sDiffCommand.execute(session, List.of(KEY_A, KEY_B, KEY_C))));
    }

    @Test
    void binaryMembersAreDistinctByByteContent() throws CommandException {
        byte[] one = new byte[] {'a'};
        byte[] two = new byte[] {'a', 0};
        sAddCommand.execute(session, List.of(KEY_A, one, two));
        assertEquals(2L, integer(sCardCommand.execute(session, List.of(KEY_A))));
        assertEquals(1L, integer(sIsMemberCommand.execute(session, List.of(KEY_A, two))));
    }

    @Test
    void commandsRejectWrongType() throws StorageException {
        store.put(KEY_A, new StringRedisValue("s".getBytes(StandardCharsets.UTF_8)));
        CommandException failure =
                assertThrows(
                        CommandException.class,
                        () -> sAddCommand.execute(session, List.of(KEY_A, mem("m"))));
        assertEquals(SetValueCommandSupport.WRONGTYPE, failure.clientMessage());

        CommandException interFailure =
                assertThrows(
                        CommandException.class,
                        () -> sInterCommand.execute(session, List.of(KEY_A, KEY_B)));
        assertEquals(SetValueCommandSupport.WRONGTYPE, interFailure.clientMessage());
    }

    @Test
    void typeReportsSetAfterSAdd() throws CommandException {
        sAddCommand.execute(session, List.of(KEY_A, mem("v")));
        ClientReply.SimpleString reply =
                assertInstanceOf(ClientReply.SimpleString.class, typeCommand.execute(session, List.of(KEY_A)));
        assertEquals("set", reply.value());
    }

    private static long integer(ClientReply reply) {
        return ((ClientReply.Integer) reply).value();
    }

    private static Set<String> decodeSet(ClientReply reply) {
        ClientReply.Array array = assertInstanceOf(ClientReply.Array.class, reply);
        Set<String> values = new HashSet<>();
        for (byte[] member : array.elements()) {
            values.add(new String(member, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static byte[] mem(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class StubSession implements ClientSession {

        @Override
        public String id() {
            return "test";
        }

        @Override
        public java.nio.channels.SocketChannel channel() {
            throw new UnsupportedOperationException();
        }
    }
}
