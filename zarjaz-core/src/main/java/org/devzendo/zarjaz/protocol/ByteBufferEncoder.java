package org.devzendo.zarjaz.protocol;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Copyright (C) 2008-2016 Matt Gumbley, DevZendo.org http://devzendo.org
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class ByteBufferEncoder {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final LinkedList<ByteBuffer> buffers = new LinkedList<>();

    public List<ByteBuffer> getBuffers() {
        buffers.forEach(Buffer::flip);
        // TODO reset?
        return Collections.unmodifiableList(buffers);
    }

    public void writeByte(final byte b) {
        ByteBuffer currentBuffer = getCurrentBuffer();
        if (currentBuffer.remaining() == 0) {
            currentBuffer = addBuffer();
        }
        currentBuffer.put(b);
    }

    private ByteBuffer getCurrentBuffer() {
        if (buffers.size() == 0) {
            addBuffer();
        }
        return buffers.getLast();
    }

    private ByteBuffer addBuffer() {
        final ByteBuffer buffer = ByteBuffer.allocate(Protocol.BUFFER_SIZE);
        buffers.add(buffer);
        return buffer;
    }

    // Conversion of other primitive data types is faster with a buffer and a call to writeBytes, rather than
    // multiple calls to writeByte.
    public void writeBytes(final byte[] bs) {
        ByteBuffer currentBuffer = getCurrentBuffer();
        int length = bs.length;
        int offset = 0;
        int remaining = currentBuffer.remaining();
        while (length > remaining) {
            currentBuffer.put(bs, offset, remaining);
            length -= remaining;
            offset += remaining;
            currentBuffer = addBuffer();
            remaining = currentBuffer.remaining();
        }
        currentBuffer.put(bs, offset, length);
    }

    public void writeInt(final int i) {
        // >>> is ever so slightly faster than >>
        final byte[] buf = new byte[4];
        buf[0] = (byte) ((i >>> 24) & 0xff);
        buf[1] = (byte) ((i >>> 16) & 0xff);
        buf[2] = (byte) ((i >>> 8) & 0xff);
        buf[3] = (byte) (i & 0xff);
        writeBytes(buf);
    }

    public void writeBoolean(final boolean b) {
        writeByte((byte) (b ? 0x01 : 0x00));
    }

    public void writeChar(final char ch) {
        final int intval = (int) ch;
        final byte[] buf = new byte[2];
        buf[0] = (byte) ((intval >>> 8) & 0xff);
        buf[1] = (byte) (intval & 0xff);
        writeBytes(buf);
    }

    public void writeShort(final short s) {
        final byte[] buf = new byte[2];
        buf[0] = (byte) ((s >>> 8) & 0xff);
        buf[1] = (byte) (s & 0xff);
        writeBytes(buf);
    }

    public void writeFloat(final float f) {
        final int bits = Float.floatToRawIntBits(f);
        final byte[] buf = new byte[4];
        buf[0] = (byte) ((bits >>> 24) & 0xFF);
        buf[1] = (byte) ((bits >>> 16) & 0xFF);
        buf[2] = (byte) ((bits >>> 8) & 0xFF);
        buf[3] = (byte) (bits & 0xFF);
        writeBytes(buf);
    }

    public void writeDouble(final double d) {
        final long bits = Double.doubleToRawLongBits(d);
        final int left = (int)((bits >>> 32) & 0xFFFFFFFF);
        final int right = (int)(bits & 0xFFFFFFFF);
        final byte[] buf = new byte[8];
        buf[0] = (byte) ((left >>> 24) & 0xFF);
        buf[1] = (byte) ((left >>> 16) & 0xFF);
        buf[2] = (byte) ((left >>> 8) & 0xFF);
        buf[3] = (byte) (left & 0xFF);
        buf[4] = (byte) ((right >>> 24) & 0xFF);
        buf[5] = (byte) ((right >>> 16) & 0xFF);
        buf[6] = (byte) ((right >>> 8) & 0xFF);
        buf[7] = (byte) (right & 0xFF);
        writeBytes(buf);
    }

    public void writeLong(final long l) {
        final byte[] buf = new byte[8];
        buf[0] = (byte) ((l >>> 56) & 0xFF);
        buf[1] = (byte) ((l >>> 48) & 0xFF);
        buf[2] = (byte) ((l >>> 40) & 0xFF);
        buf[3] = (byte) ((l >>> 32) & 0xFF);
        buf[4] = (byte) ((l >>> 24) & 0xFF);
        buf[5] = (byte) ((l >>> 16) & 0xFF);
        buf[6] = (byte) ((l >>> 8) & 0xFF);
        buf[7] = (byte) (l & 0xFF);
        writeBytes(buf);
    }

    public void writeString(final String s) {
        // TODO test for this, but, fix it.
        if (s == null) {
            throw new IllegalArgumentException("Null objects cannot be serialised");
        }
        if (s.length() == 0) {
            writeInt(0);
            return;
        }

        final byte[] utf8bytes = s.getBytes(UTF8);
        writeInt(utf8bytes.length);
        writeBytes(utf8bytes);
    }

    public void writeObject(final Class parameterType, final Object parameterValue) {
        // TODO test for this, but, fix it.
        if (parameterValue == null) {
            throw new IllegalArgumentException("Null objects cannot be serialised");
        }
        // TODO would a lookup/dispatch map be faster?
        if (parameterType.equals(Byte.class) || parameterType.equals(Byte.TYPE)) {
            writeByte((byte) parameterValue);
        } else if (parameterType.equals(Integer.class) || parameterType.equals(Integer.TYPE)) {
            writeInt((int) parameterValue);
        } else if (parameterType.equals(Boolean.class) || parameterType.equals(Boolean.TYPE)) {
            writeBoolean((boolean) parameterValue);
        } else if (parameterType.equals(String.class)) {
            writeString((String) parameterValue);
            // TODO write the other dispatcher calls for the other primitive types
        } else {
            // TODO test for this
            throw new IllegalArgumentException("The parameter type '" + parameterType.getName() + "' has no known serialisation");
        }
    }
}
