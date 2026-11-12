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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HashCommandTest {

    private static final byte[] KEY = "user:1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MISSING = "user:none".getBytes(StandardCharsets.UTF_8);

    private InMemoryKeyValueStore store;
    private HSetCommand hSetCommand;
    private HGetCommand hGetCommand;
    private HMGetCommand hmGetCommand;
    private HGetAllCommand hGetAllCommand;
    private HDelCommand hDelCommand;
    private HExistsCommand hExistsCommand;
    private HLenCommand hLenCommand;
    private HKeysCommand hKeysCommand;
    private HValsCommand hValsCommand;
    private TypeCommand typeCommand;
    private ClientSession session;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        hSetCommand = new HSetCommand(store);
        hGetCommand = new HGetCommand(store);
        hmGetCommand = new HMGetCommand(store);
        hGetAllCommand = new HGetAllCommand(store);
        hDelCommand = new HDelCommand(store);
        hExistsCommand = new HExistsCommand(store);
        hLenCommand = new HLenCommand(store);
        hKeysCommand = new HKeysCommand(store);
        hValsCommand = new HValsCommand(store);
        typeCommand = new TypeCommand(store);
        session = new StubSession();
    }

    @Test
    void hsetCreatesHashAndCountsOnlyNewFields() throws CommandException {
        assertEquals(2L, integer(hSetCommand.execute(session, List.of(KEY, field("name"), val("Ann"), field("id"), val("1")))));
        assertEquals(0L, integer(hSetCommand.execute(session, List.of(KEY, field("name"), val("Amy")))));
        assertEquals(1L, integer(hSetCommand.execute(session, List.of(KEY, field("name"), val("Amy"), field("role"), val("admin")))));
        assertEquals("Amy", bulk(hGetCommand.execute(session, List.of(KEY, field("name")))));
    }

    @Test
    void hgetAndHmgetReturnNilForMissingKeyOrField() throws CommandException {
        assertInstanceOf(ClientReply.NullBulk.class, hGetCommand.execute(session, List.of(MISSING, field("x"))));
        hSetCommand.execute(session, List.of(KEY, field("a"), val("1")));
        assertInstanceOf(ClientReply.NullBulk.class, hGetCommand.execute(session, List.of(KEY, field("missing"))));

        ClientReply.Array hmget = array(hmGetCommand.execute(session, List.of(KEY, field("a"), field("z"), field("b"))));
        assertEquals("1", stringAt(hmget, 0));
        assertNull(hmget.elements().get(1));
        assertNull(hmget.elements().get(2));
    }

    @Test
    void hgetallHkeysAndHvalsOnMissingKeyReturnEmptyArrays() throws CommandException {
        assertEquals(0, array(hGetAllCommand.execute(session, List.of(MISSING))).elements().size());
        assertEquals(0, array(hKeysCommand.execute(session, List.of(MISSING))).elements().size());
        assertEquals(0, array(hValsCommand.execute(session, List.of(MISSING))).elements().size());
        assertEquals(0L, integer(hLenCommand.execute(session, List.of(MISSING))));
        assertEquals(0L, integer(hExistsCommand.execute(session, List.of(MISSING, field("f")))));
    }

    @Test
    void hdelHexistsAndHlenReflectFieldState() throws CommandException {
        hSetCommand.execute(session, List.of(KEY, field("a"), val("1"), field("b"), val("2")));
        assertEquals(1L, integer(hExistsCommand.execute(session, List.of(KEY, field("a")))));
        assertEquals(1L, integer(hDelCommand.execute(session, List.of(KEY, field("a"), field("z")))));
        assertEquals(1L, integer(hLenCommand.execute(session, List.of(KEY))));
        assertEquals(0L, integer(hExistsCommand.execute(session, List.of(KEY, field("a")))));
    }

    @Test
    void hgetallReturnsFieldValuePairsSortedByField() throws CommandException {
        hSetCommand.execute(session, List.of(KEY, field("z"), val("3"), field("a"), val("1"), field("m"), val("2")));
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("a", "1");
        expected.put("m", "2");
        expected.put("z", "3");
        assertEquals(expected, decodePairs(array(hGetAllCommand.execute(session, List.of(KEY)))));
        assertEquals(List.of("a", "m", "z"), decodeList(array(hKeysCommand.execute(session, List.of(KEY)))));
        assertEquals(List.of("1", "2", "3"), decodeList(array(hValsCommand.execute(session, List.of(KEY)))));
    }

    @Test
    void hsetRejectsWrongType() throws StorageException {
        store.put(KEY, new StringRedisValue("x".getBytes(StandardCharsets.UTF_8)));
        CommandException failure =
                assertThrows(
                        CommandException.class,
                        () -> hSetCommand.execute(session, List.of(KEY, field("f"), val("v"))));
        assertEquals(HashCommandSupport.WRONGTYPE, failure.clientMessage());
    }

    @Test
    void typeReportsHash() throws CommandException {
        hSetCommand.execute(session, List.of(KEY, field("f"), val("v")));
        assertEquals("hash", ((ClientReply.SimpleString) typeCommand.execute(session, List.of(KEY))).value());
    }

    private static long integer(ClientReply reply) {
        return ((ClientReply.Integer) reply).value();
    }

    private static String bulk(ClientReply reply) {
        return new String(((ClientReply.BulkString) reply).value(), StandardCharsets.UTF_8);
    }

    private static ClientReply.Array array(ClientReply reply) {
        return assertInstanceOf(ClientReply.Array.class, reply);
    }

    private static String stringAt(ClientReply.Array array, int index) {
        byte[] value = array.elements().get(index);
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    private static List<String> decodeList(ClientReply.Array array) {
        return array.elements().stream()
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .toList();
    }

    private static Map<String, String> decodePairs(ClientReply.Array array) {
        Map<String, String> map = new LinkedHashMap<>();
        List<byte[]> flat = array.elements();
        for (int index = 0; index < flat.size(); index += 2) {
            map.put(new String(flat.get(index), StandardCharsets.UTF_8), new String(flat.get(index + 1), StandardCharsets.UTF_8));
        }
        return map;
    }

    private static byte[] field(String name) {
        return name.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] val(String value) {
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
