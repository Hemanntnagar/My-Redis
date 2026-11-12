package com.myredis.network;

import com.myredis.protocol.Resp2Encoder;
import com.myredis.protocol.RespArray;
import com.myredis.protocol.RespBulkString;
import com.myredis.protocol.RespInteger;
import com.myredis.protocol.RespNullBulk;
import com.myredis.protocol.RespSimpleString;
import com.myredis.protocol.RespValue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Encodes {@link ClientReply} values to RESP2 bytes (shared by the connection handler and pub/sub push). */
public final class ClientReplyEncoder {

    private final Resp2Encoder encoder = new Resp2Encoder();

    public void encode(ClientReply reply, ByteBuffer output) {
        switch (reply) {
            case ClientReply.SimpleString simple -> encoder.encodeSimpleString(simple.value(), output);
            case ClientReply.Error error -> encoder.encodeError(error.message(), output);
            case ClientReply.BulkString bulk -> encoder.encodeBulkString(bulk.value(), output);
            case ClientReply.NullBulk ignored -> encoder.encodeNullBulk(output);
            case ClientReply.Integer integer -> encoder.encodeInteger(integer.value(), output);
            case ClientReply.Array array -> encodeBulkStringArray(array, output);
            case ClientReply.CompositeArray composite -> encodeCompositeArray(composite, output);
            case ClientReply.Sequence sequence -> {
                for (ClientReply element : sequence.replies()) {
                    encode(element, output);
                }
            }
        }
    }

    public int requiredBytes(ClientReply reply) {
        return switch (reply) {
            case ClientReply.SimpleString simple -> simple.value().length() + 16;
            case ClientReply.Error error -> error.message().length() + 16;
            case ClientReply.BulkString bulk -> bulk.value().length + 32;
            case ClientReply.NullBulk ignored -> 16;
            case ClientReply.Integer integer -> 32;
            case ClientReply.Array array -> bulkArrayCapacity(array);
            case ClientReply.CompositeArray composite -> compositeArrayCapacity(composite);
            case ClientReply.Sequence sequence -> {
                int bytes = 0;
                for (ClientReply element : sequence.replies()) {
                    bytes += requiredBytes(element);
                }
                yield bytes;
            }
        };
    }

    private void encodeBulkStringArray(ClientReply.Array array, ByteBuffer output) {
        List<RespValue> elements = new ArrayList<>(array.elements().size());
        for (byte[] element : array.elements()) {
            if (element == null) {
                elements.add(RespNullBulk.INSTANCE);
            } else {
                elements.add(new RespBulkString(element));
            }
        }
        encoder.encodeArray(new RespArray(elements), output);
    }

    private void encodeCompositeArray(ClientReply.CompositeArray array, ByteBuffer output) {
        int start = output.position();
        List<RespValue> elements = new ArrayList<>(array.elements().size());
        for (ClientReply element : array.elements()) {
            elements.add(encodeAsRespValue(element));
        }
        encoder.encodeArray(new RespArray(elements), output);
        if (output.position() == start) {
            encoder.encodeArray(new RespArray(List.of()), output);
        }
    }

    private RespValue encodeAsRespValue(ClientReply reply) {
        return switch (reply) {
            case ClientReply.SimpleString simple -> new RespSimpleString(simple.value());
            case ClientReply.Error error -> new com.myredis.protocol.RespError(error.message());
            case ClientReply.BulkString bulk -> new RespBulkString(bulk.value());
            case ClientReply.NullBulk ignored -> RespNullBulk.INSTANCE;
            case ClientReply.Integer integer -> new RespInteger(integer.value());
            case ClientReply.Array array -> toRespArray(array);
            case ClientReply.CompositeArray composite -> new RespArray(toRespValues(composite.elements()));
            case ClientReply.Sequence ignored -> throw new IllegalArgumentException("sequence cannot be nested in RESP array");
        };
    }

    private RespArray toRespArray(ClientReply.Array array) {
        return new RespArray(toRespValuesFromBytes(array.elements()));
    }

    private List<RespValue> toRespValues(List<ClientReply> replies) {
        List<RespValue> values = new ArrayList<>(replies.size());
        for (ClientReply reply : replies) {
            values.add(encodeAsRespValue(reply));
        }
        return values;
    }

    private List<RespValue> toRespValuesFromBytes(List<byte[]> elements) {
        List<RespValue> values = new ArrayList<>(elements.size());
        for (byte[] element : elements) {
            if (element == null) {
                values.add(RespNullBulk.INSTANCE);
            } else {
                values.add(new RespBulkString(element));
            }
        }
        return values;
    }

    private static int bulkArrayCapacity(ClientReply.Array array) {
        int bytes = 32;
        for (byte[] element : array.elements()) {
            bytes += element == null ? 16 : element.length + 32;
        }
        return bytes;
    }

    private static int compositeArrayCapacity(ClientReply.CompositeArray array) {
        int bytes = 32;
        for (ClientReply element : array.elements()) {
            bytes += estimateNested(element);
        }
        return bytes;
    }

    private static int estimateNested(ClientReply reply) {
        return switch (reply) {
            case ClientReply.SimpleString simple -> simple.value().length() + 16;
            case ClientReply.Error error -> error.message().length() + 16;
            case ClientReply.BulkString bulk -> bulk.value().length + 32;
            case ClientReply.NullBulk ignored -> 16;
            case ClientReply.Integer integer -> 32;
            case ClientReply.Array array -> bulkArrayCapacity(array);
            case ClientReply.CompositeArray composite -> compositeArrayCapacity(composite);
            case ClientReply.Sequence sequence -> {
                int bytes = 0;
                for (ClientReply element : sequence.replies()) {
                    bytes += estimateNested(element);
                }
                yield bytes;
            }
        };
    }
}
