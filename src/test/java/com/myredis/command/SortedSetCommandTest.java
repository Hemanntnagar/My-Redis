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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SortedSetCommandTest {

    private static final byte[] KEY = "z:leaderboard".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MISSING = "z:missing".getBytes(StandardCharsets.UTF_8);

    private InMemoryKeyValueStore store;
    private ZAddCommand zAddCommand;
    private ZRemCommand zRemCommand;
    private ZScoreCommand zScoreCommand;
    private ZRankCommand zRankCommand;
    private ZRevRankCommand zRevRankCommand;
    private ZRangeCommand zRangeCommand;
    private ZCardCommand zCardCommand;
    private TypeCommand typeCommand;
    private ClientSession session;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeyValueStore();
        zAddCommand = new ZAddCommand(store);
        zRemCommand = new ZRemCommand(store);
        zScoreCommand = new ZScoreCommand(store);
        zRankCommand = new ZRankCommand(store);
        zRevRankCommand = new ZRevRankCommand(store);
        zRangeCommand = new ZRangeCommand(store);
        zCardCommand = new ZCardCommand(store);
        typeCommand = new TypeCommand(store);
        session = new StubSession();
    }

    @Test
    void zaddCountsOnlyNewMembersAndUpdatesScores() throws CommandException {
        assertEquals(
                2L,
                integer(
                        zAddCommand.execute(
                                session,
                                List.of(KEY, score("1"), mem("a"), score("2"), mem("b")))));
        assertEquals(
                0L,
                integer(zAddCommand.execute(session, List.of(KEY, score("1"), mem("a"), score("3"), mem("b")))));
        assertEquals(0L, integer(zAddCommand.execute(session, List.of(KEY, score("5"), mem("b")))));
        assertEquals(
                1L,
                integer(zAddCommand.execute(session, List.of(KEY, score("2"), mem("b"), score("4"), mem("c")))));
        assertEquals(3L, integer(zCardCommand.execute(session, List.of(KEY))));
    }

    @Test
    void orderingUsesScoreThenLexicographicalTieBreak() throws CommandException {
        zAddCommand.execute(
                session,
                List.of(
                        KEY,
                        score("1"),
                        mem("z"),
                        score("1"),
                        mem("a"),
                        score("1"),
                        mem("m"),
                        score("2"),
                        mem("solo")));
        assertEquals(List.of("a", "m", "z", "solo"), decodeList(zRangeCommand.execute(session, List.of(KEY, el("0"), el("-1")))));
        assertEquals(0L, integer(zRankCommand.execute(session, List.of(KEY, mem("a")))));
        assertEquals(1L, integer(zRankCommand.execute(session, List.of(KEY, mem("m")))));
        assertEquals(2L, integer(zRankCommand.execute(session, List.of(KEY, mem("z")))));
        assertEquals(3L, integer(zRankCommand.execute(session, List.of(KEY, mem("solo")))));
        assertEquals(3L, integer(zRevRankCommand.execute(session, List.of(KEY, mem("a")))));
        assertEquals(0L, integer(zRevRankCommand.execute(session, List.of(KEY, mem("solo")))));
    }

    @Test
    void zscoreAndRankNilForMissingKeyOrMember() throws CommandException {
        assertInstanceOf(ClientReply.NullBulk.class, zScoreCommand.execute(session, List.of(MISSING, mem("x"))));
        assertInstanceOf(ClientReply.NullBulk.class, zRankCommand.execute(session, List.of(MISSING, mem("x"))));
        assertInstanceOf(ClientReply.NullBulk.class, zRevRankCommand.execute(session, List.of(MISSING, mem("x"))));

        zAddCommand.execute(session, List.of(KEY, score("1"), mem("only")));
        assertInstanceOf(ClientReply.NullBulk.class, zScoreCommand.execute(session, List.of(KEY, mem("ghost"))));
        assertInstanceOf(ClientReply.NullBulk.class, zRankCommand.execute(session, List.of(KEY, mem("ghost"))));
        assertEquals("1", bulk(zScoreCommand.execute(session, List.of(KEY, mem("only")))));
        assertEquals(0L, integer(zRankCommand.execute(session, List.of(KEY, mem("only")))));
    }

    @Test
    void zremReturnsRemovedCount() throws CommandException {
        zAddCommand.execute(session, List.of(KEY, score("1"), mem("a"), score("2"), mem("b"), score("3"), mem("c")));
        assertEquals(2L, integer(zRemCommand.execute(session, List.of(KEY, mem("a"), mem("z"), mem("b")))));
        assertEquals(0L, integer(zRemCommand.execute(session, List.of(MISSING, mem("a")))));
        assertEquals(List.of("c"), decodeList(zRangeCommand.execute(session, List.of(KEY, el("0"), el("-1")))));
    }

    @Test
    void zrangeNormalizesIndexesLikeList() throws CommandException {
        zAddCommand.execute(
                session,
                List.of(
                        KEY,
                        score("0"),
                        mem("w"),
                        score("1"),
                        mem("x"),
                        score("2"),
                        mem("y"),
                        score("3"),
                        mem("z")));
        assertEquals(List.of("x", "y"), decodeList(zRangeCommand.execute(session, List.of(KEY, el("1"), el("2")))));
        assertEquals(List.of("y"), decodeList(zRangeCommand.execute(session, List.of(KEY, el("-2"), el("-2")))));
        assertEquals(List.of(), decodeList(zRangeCommand.execute(session, List.of(MISSING, el("0"), el("-1")))));
    }

    @Test
    void binaryMembersAreDistinct() throws CommandException {
        byte[] one = new byte[] {'a'};
        byte[] two = new byte[] {'a', 0};
        zAddCommand.execute(session, List.of(KEY, score("1"), one, score("1"), two));
        assertEquals(2L, integer(zCardCommand.execute(session, List.of(KEY))));
        assertEquals("1", bulk(zScoreCommand.execute(session, List.of(KEY, two))));
    }

    @Test
    void rejectsInvalidScoresAndWrongType() throws StorageException {
        CommandException badScore =
                assertThrows(
                        CommandException.class,
                        () -> zAddCommand.execute(session, List.of(KEY, score("nope"), mem("m"))));
        assertEquals(SortedSetCommandSupport.NOT_A_FLOAT, badScore.clientMessage());

        store.put(KEY, new StringRedisValue("s".getBytes(StandardCharsets.UTF_8)));
        CommandException wrongType =
                assertThrows(
                        CommandException.class,
                        () -> zAddCommand.execute(session, List.of(KEY, score("1"), mem("m"))));
        assertEquals(SortedSetCommandSupport.WRONGTYPE, wrongType.clientMessage());
    }

    @Test
    void typeReportsZsetAfterZAdd() throws CommandException {
        zAddCommand.execute(session, List.of(KEY, score("1"), mem("m")));
        ClientReply.SimpleString reply =
                assertInstanceOf(ClientReply.SimpleString.class, typeCommand.execute(session, List.of(KEY)));
        assertEquals("zset", reply.value());
    }

    private static long integer(ClientReply reply) {
        return ((ClientReply.Integer) reply).value();
    }

    private static String bulk(ClientReply reply) {
        ClientReply.BulkString bulk = assertInstanceOf(ClientReply.BulkString.class, reply);
        return new String(bulk.value(), StandardCharsets.US_ASCII);
    }

    private static List<String> decodeList(ClientReply reply) {
        ClientReply.Array array = assertInstanceOf(ClientReply.Array.class, reply);
        List<String> values = new ArrayList<>(array.elements().size());
        for (byte[] element : array.elements()) {
            values.add(new String(element, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static byte[] mem(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] score(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] el(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
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
