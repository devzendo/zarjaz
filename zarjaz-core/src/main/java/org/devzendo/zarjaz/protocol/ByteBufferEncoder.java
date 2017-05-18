package org.devzendo.zarjaz.protocol;

import org.devzendo.zarjaz.nio.DefaultWritableByteBuffer;
import org.devzendo.zarjaz.nio.ReadableByteBuffer;
import org.devzendo.zarjaz.nio.WritableByteBuffer;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

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

    private final LinkedList<WritableByteBuffer> buffers = new LinkedList<>();

    // TODO this is wrong? should this be flipping? this is the current behaviour, so replicate it with the current right type
    // then refactoring will be made much easier as I can lean on the compiler to find bad users.
    public List<ReadableByteBuffer> getBuffers() {
        return Collections.unmodifiableList(buffers.stream().map(WritableByteBuffer::flip).collect(Collectors.toList()));
        // TODO reset?
    }

    public void writeByte(final byte b) {
        WritableByteBuffer currentBuffer = getCurrentBuffer();
        if (currentBuffer.remaining() == 0) {
            currentBuffer = addBuffer();
        }
        currentBuffer.put(b);
    }

    private WritableByteBuffer getCurrentBuffer() {
        if (buffers.size() == 0) {
            addBuffer();
        }
        return buffers.getLast();
    }

    private WritableByteBuffer addBuffer() {
        final WritableByteBuffer buffer = DefaultWritableByteBuffer.allocate(Protocol.BUFFER_SIZE);
        buffers.add(buffer);
        return buffer;
    }

    // Conversion of other primitive data types is faster with a buffer and a call to writeBytes, rather than
    // multiple calls to writeByte.
    public void writeBytes(final byte[] bs) {
        WritableByteBuffer currentBuffer = getCurrentBuffer();
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
        writeInt(Float.floatToRawIntBits(f));
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

        final byte[] utf8bytes = s.getBytes(Protocol.UTF8);
        writeInt(utf8bytes.length);
        writeBytes(utf8bytes);
    }

    public void writeObject(final Class parameterType, final Object parameterValue) {
        // TODO test for this, but, fix it.
        if (parameterValue == null) {
            throw new IllegalArgumentException("Null objects cannot be serialised");
        }
        final Class parameterValueType = parameterValue.getClass();
        boolean fail = false;
        // TODO would a lookup/dispatch map be faster?
        // it would certainly be cleaner
        // TODO parameterValue might be of a narrower type - conversion map might be best
        // TODO parameterValue could be of a wider type
        // TODO parameterValue could be of a completetly weird wrong type
        if (parameterType.equals(Byte.class) || parameterType.equals(Byte.TYPE)) {
            if (parameterValueType.equals(Byte.class) || parameterValueType.equals(Byte.TYPE)) {
                writeByte((byte) parameterValue);
            } else {
                fail = true;
            }

        } else if (parameterType.equals(Short.class) || parameterType.equals(Short.TYPE)) {
            if (parameterValueType.equals(Short.class) || parameterValueType.equals(Short.TYPE)) {
                writeShort((short) parameterValue);
            } else {
                fail = true;
            }

        } else if (parameterType.equals(Character.class) || parameterType.equals(Character.TYPE)) {
            if (parameterValueType.equals(Character.class) || parameterValueType.equals(Character.TYPE)) {
                writeChar((char) parameterValue);
            } else {
                fail = true;
            }

        } else if (parameterType.equals(Float.class) || parameterType.equals(Float.TYPE)) {
            if (parameterValueType.equals(Float.class) || parameterValueType.equals(Float.TYPE)) {
                writeFloat((float) parameterValue);
            } else {
                fail = true;
            }

        } else if (parameterType.equals(Double.class) || parameterType.equals(Double.TYPE)) {
            if (parameterValueType.equals(Double.class) || parameterValueType.equals(Double.TYPE)) {
                writeDouble((double) parameterValue);
            } else {
                fail = true;
            }

        } else if (parameterType.equals(Integer.class) || parameterType.equals(Integer.TYPE)) {
            if (parameterValueType.equals(Integer.class) || parameterValueType.equals(Integer.TYPE)) {
                writeInt((int) parameterValue);
            } else if (parameterValueType.equals(Byte.class) || parameterValueType.equals(Byte.TYPE)) {
                writeInt((byte) parameterValue);
            } else {
                fail = true;
            }

        } else if (parameterType.equals(Long.class) || parameterType.equals(Long.TYPE)) {
            if (parameterValueType.equals(Long.class) || parameterValueType.equals(Long.TYPE)) {
                writeLong((long) parameterValue);
            } else {
                fail = true;
            }

        } else if (parameterType.equals(Boolean.class) || parameterType.equals(Boolean.TYPE)) {
            if (parameterValueType.equals(Boolean.class) || parameterValueType.equals(Boolean.TYPE)) {
                writeBoolean((boolean) parameterValue);
            } else {
                fail = true;
            }

        } else if (parameterType.equals(String.class)) {
            if (parameterValueType.equals(String.class)) {
                writeString((String) parameterValue);
            } else {
                fail = true;
            }
            // TODO write the other dispatcher calls for the other primitive types

        } else {
            // TODO test for this
            throw new IllegalArgumentException("The parameter type '" + parameterType.getName() + "' has no known serialisation");
        }

        if (fail) {
            throw new IllegalArgumentException("The parameter value type '" + parameterValueType.getSimpleName() +
                    "' cannot be converted to the parameter type '" + parameterType.getSimpleName() + "'");
        }
    }
}
