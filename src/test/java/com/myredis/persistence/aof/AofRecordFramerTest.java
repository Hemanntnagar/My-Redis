package com.myredis.persistence.aof;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AofRecordFramerTest {

    @Test
    void ignoresIncompleteTrailingFrame() {
        byte[] command = AofRespCommandCodec.encode(List.of("SET".getBytes(StandardCharsets.UTF_8), "k".getBytes(), "v".getBytes()));
        byte[] framed = AofRecordFramer.frame(command);
        byte[] file = new byte[AofRecordFramer.headerLength() + framed.length - 3];
        System.arraycopy(AofRecordFramer.fileHeader(), 0, file, 0, AofRecordFramer.headerLength());
        System.arraycopy(framed, 0, file, AofRecordFramer.headerLength(), framed.length - 3);

        AofRecordFramer.ReadResult result = AofRecordFramer.readAllRecords(file);

        assertEquals(0, result.commandPayloads().size());
        assertTrue(result.ignoredTrailingBytes() > 0);
    }

    @Test
    void readsCompleteRecords() {
        byte[] command = AofRespCommandCodec.encode(List.of("SET".getBytes(StandardCharsets.UTF_8), "k".getBytes(), "v".getBytes()));
        byte[] framed = AofRecordFramer.frame(command);
        byte[] file = new byte[AofRecordFramer.headerLength() + framed.length];
        System.arraycopy(AofRecordFramer.fileHeader(), 0, file, 0, AofRecordFramer.headerLength());
        System.arraycopy(framed, 0, file, AofRecordFramer.headerLength(), framed.length);

        AofRecordFramer.ReadResult result = AofRecordFramer.readAllRecords(file);

        assertEquals(1, result.commandPayloads().size());
        assertEquals(0, result.ignoredTrailingBytes());
    }
}
