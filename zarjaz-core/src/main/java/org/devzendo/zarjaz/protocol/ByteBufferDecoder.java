package org.devzendo.zarjaz.protocol;

import org.devzendo.commoncode.string.HexDump;
import org.devzendo.zarjaz.nio.ReadableByteBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
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
public class ByteBufferDecoder {
    private final List<ReadableByteBuffer> buffers;
    private int currentBuffer = 0;

    public ByteBufferDecoder(final List<ReadableByteBuffer> buffers) {
        this.buffers = buffers;
    }

    public boolean empty() {
        final ReadableByteBuffer buffer = getBuffer();
        return buffer == null || buffer.remaining() == 0;
    }

    public int size() {
        int size = 0;
        for (ReadableByteBuffer buffer : buffers) {
            size += buffer.remaining();
        }
        return size;
    }

    private ReadableByteBuffer getBuffer() {
        while (currentBuffer < buffers.size()) {
            ReadableByteBuffer buffer = buffers.get(currentBuffer);
            if (buffer.hasRemaining())
                return buffer;
            currentBuffer++;
        }
        return null;
    }

    public byte readByte() throws IOException {
        final ReadableByteBuffer buffer = getBuffer();
        if (buffer == null || buffer.remaining() == 0) {
            exhausted(1);
        }
        return buffer.get();
    }

    public void readBytes(final byte[] dest, final int length) throws IOException {
        int remaining = length;
        int copyOffset = 0;
        while (remaining > 0) {
            final ReadableByteBuffer buffer = getBuffer();
            if (buffer == null || buffer.remaining() == 0) {
                exhausted(remaining);
            }
            int copyLength = Math.min(remaining, buffer.remaining());
            buffer.get(dest, copyOffset, copyLength);
            copyOffset += copyLength;
            remaining -= copyLength;
        }
    }

    public boolean readBoolean() throws IOException {
        return readByte() == (byte) 0x01;
    }

    public char readChar() throws IOException {
        final byte[] buf = new byte[2];
        readBytes(buf, 2);
        return (char) ((buf[0] << 8) | buf[1]);
    }

    public short readShort() throws IOException {
        final byte[] buf = new byte[2];
        readBytes(buf, 2);
        return (short) ((buf[0] << 8) | buf[1]);
    }

    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    public int readInt() throws IOException {
        final byte[] buf = new byte[4];
        readBytes(buf, 4);
        return ((buf[0] << 24) & 0xff000000) |
               ((buf[1] << 16) & 0x00ff0000) |
               ((buf[2] << 8)  & 0x0000ff00) |
               ( buf[3]        & 0x000000ff);
    }

    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    public long readLong() throws IOException {
        final byte[] buf = new byte[8];
        readBytes(buf, 8);
        long out = (((long) buf[0] << 56) & 0xff00000000000000L) |
                   (((long) buf[1] << 48) & 0x00ff000000000000L) |
                   (((long) buf[2] << 40) & 0x0000ff0000000000L) |
                   (((long) buf[3] << 32) & 0x000000ff00000000L) |
                   (((long) buf[4] << 24) & 0x00000000ff000000L) |
                   (((long) buf[5] << 16) & 0x0000000000ff0000L) |
                   (((long) buf[6] << 8)  & 0x000000000000ff00L) |
                   ( (long) buf[7]        & 0x00000000000000ffL);
        return out;
    }

    public String readString() throws IOException {
        final int length = readInt();
        final byte[] buf = new byte[length];
        readBytes(buf, length);
        return new String(buf, 0, length, Protocol.UTF8);
    }

    private void exhausted(final int requiredBytes) throws IOException {
        throw new IOException("Buffers exhausted; " + requiredBytes + " byte(s) required");
    }

    public Object readObject(final Class parameterType) throws IOException {
        // TODO would a lookup/dispatch map be faster?
        if (parameterType.equals(Byte.class) || parameterType.equals(Byte.TYPE)) {
            return readByte();
        } else if (parameterType.equals(Integer.class) || parameterType.equals(Integer.TYPE)) {
            return readInt();
        } else if (parameterType.equals(Long.class) || parameterType.equals(Long.TYPE)) {
            return readLong();
        } else if (parameterType.equals(Boolean.class) || parameterType.equals(Boolean.TYPE)) {
            return readBoolean();
        } else if (parameterType.equals(String.class)) {
            return readString();
            // TODO write the other dispatcher calls for the other primitive types
        } else {
            // TODO test for this
            throw new IllegalArgumentException("The parameter type '" + parameterType.getName() + "' has no known deserialisation");
        }
    }
}
