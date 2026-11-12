package com.myredis.protocol;

import java.util.List;

/**
 * RESP2 array reply ({@code *<n>}) with heterogeneous child values.
 */
public record RespArray(List<RespValue> elements) implements RespValue {

    public RespArray {
        elements = List.copyOf(elements);
    }
}
