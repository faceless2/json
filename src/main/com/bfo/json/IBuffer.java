package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.text.*;
import java.util.*;

class IBuffer extends Core {

    private final ByteBuffer value;

    IBuffer(ByteBuffer value) {
        this.value = value;
    }

    @Override Object value() {
        return value;
    }

    @Override String type() {
        return "buffer";
    }

    @Override String stringValue(Json json) {
        StringBuilder sb = new StringBuilder(value.remaining() * 4 / 3 + 2);
        try {
            Base64OutputStream out = new Base64OutputStream(sb);
            json.writeBuffer(out);
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    @Override ByteBuffer bufferValue(Json json) {
        return value;
    }

    @Override public boolean equals(Object o) {
        return o instanceof IBuffer && ((IBuffer)o).value.position(0).equals(value.position(0));
    }

    @Override void write(Json json, Appendable sb, SerializerState state) throws IOException {
        sb.append('"');
        int len = state.options == null ? 0 : state.options.getMaxStringLength();
        if (len == 0) {
            Base64OutputStream out = new Base64OutputStream(sb);
            json.writeBuffer(out);
            out.close();
        } else {
            sb.append("(" + value.limit() + " bytes)");
        }
        sb.append('"');
    }

}
