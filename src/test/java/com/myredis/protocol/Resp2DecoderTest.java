package com.myredis.protocol;

import com.myredis.exception.ProtocolException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Resp2DecoderTest {

    private final Resp2Decoder decoder = new Resp2Decoder();

    @Test
    void decodesSimpleStringAndErrorReplies() throws ProtocolException {
        ByteBuffer simple = ByteBuffer.wrap("+PONG\r\n".getBytes(StandardCharsets.UTF_8));
        Object simpleDecoded = decoder.decodeNext(simple);
        assertInstanceOf(RespSimpleString.class, simpleDecoded);
        assertEquals("PONG", ((RespSimpleString) simpleDecoded).value());

        ByteBuffer error = ByteBuffer.wrap("-ERR boom\r\n".getBytes(StandardCharsets.UTF_8));
        Object errorDecoded = decoder.decodeNext(error);
        assertInstanceOf(RespError.class, errorDecoded);
        assertEquals("ERR boom", ((RespError) errorDecoded).message());
    }

    @Test
    void rejectsUnknownRespTypeByte() {
        ByteBuffer buffer = ByteBuffer.wrap("?nope\r\n".getBytes(StandardCharsets.UTF_8));
        ProtocolException failure = assertThrows(ProtocolException.class, () -> decoder.decodeNext(buffer));
        assertEquals("unknown RESP type byte", failure.clientMessage());
    }

    @Test
    void decodesIntegerAndBulkReplies() throws ProtocolException {
        ByteBuffer integer = ByteBuffer.wrap(":99\r\n".getBytes(StandardCharsets.UTF_8));
        Object integerDecoded = decoder.decodeNext(integer);
        assertInstanceOf(RespInteger.class, integerDecoded);
        assertEquals(99L, ((RespInteger) integerDecoded).value());

        byte[] binary = new byte[] {0x00, 'x'};
        ByteBuffer bulkFrame = ByteBuffer.allocate(32);
        Resp2BulkStringEncoder.encode(binary, bulkFrame);
        bulkFrame.flip();
        Object bulkDecoded = decoder.decodeNext(bulkFrame);
        assertInstanceOf(RespBulkString.class, bulkDecoded);
        assertArrayEquals(binary, ((RespBulkString) bulkDecoded).data());

        Object nullBulk = decoder.decodeNext(ByteBuffer.wrap("$-1\r\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals(RespNullBulk.INSTANCE, nullBulk);
    }

    @Test
    void bulkDecodeIsIncremental() throws ProtocolException {
        byte[] frame = "$3\r\nfoo\r\n".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(frame, 0, 6);
        assertNull(decoder.decodeNext(buffer));
        buffer = ByteBuffer.wrap(frame);
        assertInstanceOf(RespBulkString.class, decoder.decodeNext(buffer));
    }

    @Test
    void decodesCommandArrayIncrementally() throws ProtocolException {
        byte[] frame = "*2\r\n$4\r\nPING\r\n$4\r\nPONG\r\n".getBytes(StandardCharsets.UTF_8);

        ByteBuffer firstChunk = ByteBuffer.wrap(frame, 0, 10);
        assertNull(decoder.decodeNext(firstChunk));

        ByteBuffer full = ByteBuffer.wrap(frame);
        Object decoded = decoder.decodeNext(full);
        assertTrue(decoded instanceof List<?>);
        @SuppressWarnings("unchecked")
        List<byte[]> parts = (List<byte[]>) decoded;
        assertEquals(2, parts.size());
        assertEquals("PING", new String(parts.get(0), StandardCharsets.UTF_8));
        assertEquals("PONG", new String(parts.get(1), StandardCharsets.UTF_8));
        assertNull(decoder.decodeNext(full));
    }
}
