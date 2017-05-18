package org.devzendo.zarjaz.protocol;

import org.devzendo.zarjaz.nio.ReadableByteBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
public class TestByteBufferEncoder {
    private static final int BUFFER_SIZE = Protocol.BUFFER_SIZE; // white box

    private final ByteBufferEncoder encoder = new ByteBufferEncoder();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void emptyBuffers() {
        assertThat(encoder.getBuffers(), empty());
    }

    @Test
    public void singleByte() {
        encoder.writeByte((byte) 0xc9);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(1);
        assertThat(buffer.get(0), equalTo((byte) 0xc9));
    }

    @Test
    public void aFullBufferOfBytes() {
        final byte[] buf = new byte[BUFFER_SIZE];
        for (int i=0; i<BUFFER_SIZE; i++) {
            buf[i] = (byte) (i & 0xff);
        }
        encoder.writeBytes(buf);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(BUFFER_SIZE);
        final byte[] dst = new byte[BUFFER_SIZE];
        buffer.get(dst);
        assertThat(dst, equalTo(buf));
    }

    @Test
    public void moreThanOneBufferOfBytesWrittenByWriteBytes() {
        final byte[] buf = initialiseMoreThanOneBufferOfBytes();
        encoder.writeBytes(buf);
        checkMoreThanOneBufferOfBytes(buf);
    }

    @Test
    public void moreThanOneBufferOfBytesWrittenByWriteByte() {
        final byte[] buf = initialiseMoreThanOneBufferOfBytes();
        for (int i=0; i < buf.length; i++) {
            encoder.writeByte(buf[i]);
        }
        checkMoreThanOneBufferOfBytes(buf);
    }

    private byte[] initialiseMoreThanOneBufferOfBytes() {
        final byte[] buf = new byte[BUFFER_SIZE + 128];
        for (int i=0; i<BUFFER_SIZE + 128; i++) {
            buf[i] = (byte) (i & 0xff);
        }
        return buf;
    }

    private void checkMoreThanOneBufferOfBytes(final byte[] buf) {
        final List<ReadableByteBuffer> buffers = encoder.getBuffers();
        assertThat(buffers, hasSize(2));

        final ReadableByteBuffer firstBuffer = buffers.get(0);
        assertThat(firstBuffer.capacity(), equalTo(BUFFER_SIZE));
        assertThat(firstBuffer.limit(), equalTo(BUFFER_SIZE));
        final byte[] firstDst = new byte[BUFFER_SIZE];
        firstBuffer.get(firstDst);
        assertThat(firstDst, equalTo(Arrays.copyOf(buf, BUFFER_SIZE)));

        final ReadableByteBuffer secondBuffer = buffers.get(1);
        assertThat(secondBuffer.capacity(), equalTo(BUFFER_SIZE));
        assertThat(secondBuffer.limit(), equalTo(128));
        final byte[] dst = new byte[128];
        secondBuffer.get(dst);
        assertThat(dst, equalTo(Arrays.copyOfRange(buf, BUFFER_SIZE, BUFFER_SIZE + 128)));
    }

    private ReadableByteBuffer getSingleByteBufferWithExpectedBytes(final int expectedBytes) {
        final List<ReadableByteBuffer> buffers = encoder.getBuffers();
        assertThat(buffers, hasSize(1));
        final ReadableByteBuffer buffer = buffers.get(0);
        assertThat(buffer.capacity(), equalTo(BUFFER_SIZE));
        assertThat(buffer.limit(), equalTo(expectedBytes));
        return buffer;
    }

    @Test
    public void singleInt() {
        encoder.writeInt(0xabcdef01);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(4);
        assertThat(buffer.get(0), equalTo((byte) 0xab));
        assertThat(buffer.get(1), equalTo((byte) 0xcd));
        assertThat(buffer.get(2), equalTo((byte) 0xef));
        assertThat(buffer.get(3), equalTo((byte) 0x01));
    }

    // As the basic data types all use writeByte/writeBytes, and that allocates more buffers when the current is full,
    // there's no need for any 'multiple buffers of int' test.

    @Test
    public void singleBoolean() {
        encoder.writeBoolean(false);
        encoder.writeBoolean(true);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(2);
        assertThat(buffer.get(0), equalTo((byte) 0x00));
        assertThat(buffer.get(1), equalTo((byte) 0x01));
    }

    @Test
    public void singleChar() {
        encoder.writeChar('A');
        encoder.writeChar('0');
        encoder.writeChar('\uffff');
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(6);
        assertThat(buffer.get(0), equalTo((byte) 0x00));
        assertThat(buffer.get(1), equalTo((byte) 0x41));
        assertThat(buffer.get(2), equalTo((byte) 0x00));
        assertThat(buffer.get(3), equalTo((byte) 0x30));
        assertThat(buffer.get(4), equalTo((byte) 0xff));
        assertThat(buffer.get(5), equalTo((byte) 0xff));
    }

    @Test
    public void singleShort() {
        encoder.writeShort((short) 0x8764);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(2);
        assertThat(buffer.get(0), equalTo((byte) 0x87));
        assertThat(buffer.get(1), equalTo((byte) 0x64));
    }

    @Test
    public void singleLong() {
        encoder.writeLong(0x87643210ABCDEF10L);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(8);
        assertThat(buffer.get(0), equalTo((byte) 0x87));
        assertThat(buffer.get(1), equalTo((byte) 0x64));
        assertThat(buffer.get(2), equalTo((byte) 0x32));
        assertThat(buffer.get(3), equalTo((byte) 0x10));
        assertThat(buffer.get(4), equalTo((byte) 0xAB));
        assertThat(buffer.get(5), equalTo((byte) 0xCD));
        assertThat(buffer.get(6), equalTo((byte) 0xEF));
        assertThat(buffer.get(7), equalTo((byte) 0x10));
    }

    @Test
    public void singleFloat() {
        encoder.writeFloat(3.1415f);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(4);
        assertThat(buffer.get(0), equalTo((byte) 64));
        assertThat(buffer.get(1), equalTo((byte) 73));
        assertThat(buffer.get(2), equalTo((byte) 14));
        assertThat(buffer.get(3), equalTo((byte) 86));
    }

    @Test
    public void singleDouble() {
        encoder.writeDouble(3.1415);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(8);
        assertThat(buffer.get(0), equalTo((byte) 64));
        assertThat(buffer.get(1), equalTo((byte) 9));
        assertThat(buffer.get(2), equalTo((byte) 33));
        assertThat(buffer.get(3), equalTo((byte) -54));
        assertThat(buffer.get(4), equalTo((byte) -64));
        assertThat(buffer.get(5), equalTo((byte) -125));
        assertThat(buffer.get(6), equalTo((byte) 18));
        assertThat(buffer.get(7), equalTo((byte) 111));
    }

    @Test
    public void encodeNullString() {
        thrown.expect(IllegalArgumentException.class);
        encoder.writeString(null);
    }

    @Test
    public void encodeEmptyString() {
        encoder.writeString("");
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(4);
        assertThat(buffer.get(0), equalTo((byte) 0));
        assertThat(buffer.get(1), equalTo((byte) 0));
        assertThat(buffer.get(2), equalTo((byte) 0));
        assertThat(buffer.get(3), equalTo((byte) 0));
    }

    // TODO test the UTF8 encoding of strings

    @Test
    public void encodeString() {
        encoder.writeString("Hello");
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(9);
        // count of bytes
        assertThat(buffer.get(0), equalTo((byte) 0));
        assertThat(buffer.get(1), equalTo((byte) 0));
        assertThat(buffer.get(2), equalTo((byte) 0));
        assertThat(buffer.get(3), equalTo((byte) 5));

        assertThat(buffer.get(4), equalTo((byte) 'H'));
        assertThat(buffer.get(5), equalTo((byte) 'e'));
        assertThat(buffer.get(6), equalTo((byte) 'l'));
        assertThat(buffer.get(7), equalTo((byte) 'l'));
        assertThat(buffer.get(8), equalTo((byte) 'o'));
    }

    @Test
    public void encodeObjectBytePrimitive() {
        encoder.writeObject(Byte.TYPE, (byte) 201);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(1);
        assertThat(buffer.get(0), equalTo((byte) 201));
    }

    @Test
    public void encodeObjectByteWrapper() {
        encoder.writeObject(Byte.class, Byte.valueOf((byte) 201));
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(1);
        assertThat(buffer.get(0), equalTo((byte) 201));
    }

    @Test
    public void encodeObjectByteWrapperIncompatibleType() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'String' cannot be converted to the parameter type 'Byte'");

        encoder.writeObject(Byte.class, "boom");
    }

    @Test
    public void encodeObjectBytePrimitiveIncompatibleType() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'String' cannot be converted to the parameter type 'byte'");

        encoder.writeObject(Byte.TYPE, "boom");
    }

    @Test
    public void encodeObjectIntPrimitive() {
        encoder.writeObject(Integer.TYPE, (int) 201);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(4);
        assertThat(buffer.get(0), equalTo((byte) 0));
        assertThat(buffer.get(1), equalTo((byte) 0));
        assertThat(buffer.get(2), equalTo((byte) 0));
        assertThat(buffer.get(3), equalTo((byte) 201));
    }

    @Test
    public void encodeObjectIntWrapper() {
        encoder.writeObject(Integer.class, Integer.valueOf(201));
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(4);
        assertThat(buffer.get(0), equalTo((byte) 0));
        assertThat(buffer.get(1), equalTo((byte) 0));
        assertThat(buffer.get(2), equalTo((byte) 0));
        assertThat(buffer.get(3), equalTo((byte) 201));
    }

    @Test
    public void encodeObjectFloatPrimitive() {
        encoder.writeObject(Float.TYPE, 3.1415f);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(4);
        assertThat(buffer.get(0), equalTo((byte) 64));
        assertThat(buffer.get(1), equalTo((byte) 73));
        assertThat(buffer.get(2), equalTo((byte) 14));
        assertThat(buffer.get(3), equalTo((byte) 86));
    }

    @Test
    public void encodeObjectFloatWrapper() {
        encoder.writeObject(Float.class, 3.1415f);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(4);
        assertThat(buffer.get(0), equalTo((byte) 64));
        assertThat(buffer.get(1), equalTo((byte) 73));
        assertThat(buffer.get(2), equalTo((byte) 14));
        assertThat(buffer.get(3), equalTo((byte) 86));
    }

    @Test
    public void encodeObjectShortPrimitive() {
        encoder.writeObject(Short.TYPE, (short) 201);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(2);
        assertThat(buffer.get(0), equalTo((byte) 0));
        assertThat(buffer.get(1), equalTo((byte) 201));
    }

    @Test
    public void encodeObjectShortWrapper() {
        encoder.writeObject(Short.class, Short.valueOf((short) 201));
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(2);
        assertThat(buffer.get(0), equalTo((byte) 0));
        assertThat(buffer.get(1), equalTo((byte) 201));
    }

    @Test
    public void encodeObjectCharacterPrimitive() {
        encoder.writeObject(Character.TYPE, (char) 'a');
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(2);
        assertThat(buffer.get(0), equalTo((byte) 0));
        assertThat(buffer.get(1), equalTo((byte) 97));
    }

    @Test
    public void encodeObjectCharacterWrapper() {
        encoder.writeObject(Character.class, Character.valueOf('a'));
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(2);
        assertThat(buffer.get(0), equalTo((byte) 0));
        assertThat(buffer.get(1), equalTo((byte) 97));
    }

    @Test
    public void encodeObjectIntPrimitiveWidenedByte() {
        encoder.writeObject(Integer.TYPE, (byte) 201);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(4);
        assertThat(buffer.get(0), equalTo((byte) -1));
        assertThat(buffer.get(1), equalTo((byte) -1));
        assertThat(buffer.get(2), equalTo((byte) -1));
        assertThat(buffer.get(3), equalTo((byte) 201));
    }

    @Test
    public void encodeObjectIntWrapperWidenedByte() {
        encoder.writeObject(Integer.class, (byte) 201);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(4);
        assertThat(buffer.get(0), equalTo((byte) -1));
        assertThat(buffer.get(1), equalTo((byte) -1));
        assertThat(buffer.get(2), equalTo((byte) -1));
        assertThat(buffer.get(3), equalTo((byte) 201));
    }

    @Test
    public void encodeObjectLongPrimitive() {
        encoder.writeObject(Long.TYPE, 0x0102030405060708L);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(8);
        assertThat(buffer.get(0), equalTo((byte) 0x01));
        assertThat(buffer.get(1), equalTo((byte) 0x02));
        assertThat(buffer.get(2), equalTo((byte) 0x03));
        assertThat(buffer.get(3), equalTo((byte) 0x04));
        assertThat(buffer.get(4), equalTo((byte) 0x05));
        assertThat(buffer.get(5), equalTo((byte) 0x06));
        assertThat(buffer.get(6), equalTo((byte) 0x07));
        assertThat(buffer.get(7), equalTo((byte) 0x08));
    }

    @Test
    public void encodeObjectLongWrapper() {
        encoder.writeObject(Long.class, Long.valueOf(0x0102030405060708L));
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(8);
        assertThat(buffer.get(0), equalTo((byte) 0x01));
        assertThat(buffer.get(1), equalTo((byte) 0x02));
        assertThat(buffer.get(2), equalTo((byte) 0x03));
        assertThat(buffer.get(3), equalTo((byte) 0x04));
        assertThat(buffer.get(4), equalTo((byte) 0x05));
        assertThat(buffer.get(5), equalTo((byte) 0x06));
        assertThat(buffer.get(6), equalTo((byte) 0x07));
        assertThat(buffer.get(7), equalTo((byte) 0x08));
    }

    @Test
    public void encodeObjectDoublePrimitive() {
        encoder.writeObject(Double.TYPE, 3.1415d);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(8);
        assertThat(buffer.get(0), equalTo((byte) 64));
        assertThat(buffer.get(1), equalTo((byte) 9));
        assertThat(buffer.get(2), equalTo((byte) 33));
        assertThat(buffer.get(3), equalTo((byte) -54));
        assertThat(buffer.get(4), equalTo((byte) -64));
        assertThat(buffer.get(5), equalTo((byte) -125));
        assertThat(buffer.get(6), equalTo((byte) 18));
        assertThat(buffer.get(7), equalTo((byte) 111));
    }

    @Test
    public void encodeObjectDoubleWrapper() {
        encoder.writeObject(Double.class, Double.valueOf(3.1415d));
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(8);
        assertThat(buffer.get(0), equalTo((byte) 64));
        assertThat(buffer.get(1), equalTo((byte) 9));
        assertThat(buffer.get(2), equalTo((byte) 33));
        assertThat(buffer.get(3), equalTo((byte) -54));
        assertThat(buffer.get(4), equalTo((byte) -64));
        assertThat(buffer.get(5), equalTo((byte) -125));
        assertThat(buffer.get(6), equalTo((byte) 18));
        assertThat(buffer.get(7), equalTo((byte) 111));
    }

    @Test
    public void encodeObjectIntWrapperIncompatibleType() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'String' cannot be converted to the parameter type 'Integer'");

        encoder.writeObject(Integer.class, "boom");
    }

    @Test
    public void encodeObjectIntPrimitiveIncompatibleType() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'String' cannot be converted to the parameter type 'int'");

        encoder.writeObject(Integer.TYPE, "boom");
    }

    @Test
    public void encodeObjectString() {
        encoder.writeObject(String.class, "Hello");
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(9);
        // count of bytes
        assertThat(buffer.get(0), equalTo((byte) 0));
        assertThat(buffer.get(1), equalTo((byte) 0));
        assertThat(buffer.get(2), equalTo((byte) 0));
        assertThat(buffer.get(3), equalTo((byte) 5));

        assertThat(buffer.get(4), equalTo((byte) 'H'));
        assertThat(buffer.get(5), equalTo((byte) 'e'));
        assertThat(buffer.get(6), equalTo((byte) 'l'));
        assertThat(buffer.get(7), equalTo((byte) 'l'));
        assertThat(buffer.get(8), equalTo((byte) 'o'));
    }

    @Test
    public void encodeStringIncompatibleType() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'Integer' cannot be converted to the parameter type 'String'");

        encoder.writeObject(String.class, Integer.valueOf(12));
    }

    @Test
    public void encodeObjectBooleanPrimitive() {
        encoder.writeObject(Boolean.TYPE, (boolean) true);
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(1);
        assertThat(buffer.get(0), equalTo((byte) 1));
    }

    @Test
    public void encodeObjectBooleanWrapper() {
        encoder.writeObject(Boolean.class, Boolean.valueOf(true));
        final ReadableByteBuffer buffer = getSingleByteBufferWithExpectedBytes(1);
        assertThat(buffer.get(0), equalTo((byte) 1));
    }

    @Test
    public void encodeObjectBooleanWrapperIncompatibleType() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'String' cannot be converted to the parameter type 'Boolean'");

        encoder.writeObject(Boolean.class, "boom");
    }

    @Test
    public void encodeObjectBooleanPrimitiveIncompatibleType() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("The parameter value type 'String' cannot be converted to the parameter type 'boolean'");

        encoder.writeObject(Boolean.TYPE, "boom");
    }

    // TODO write the other dispatcher calls for the other primitive types

}
