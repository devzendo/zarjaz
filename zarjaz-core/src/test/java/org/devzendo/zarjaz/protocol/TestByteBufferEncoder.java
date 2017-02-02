package org.devzendo.zarjaz.protocol;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

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
    private static final int BUFFER_SIZE = ByteBufferEncoder.BUFFER_SIZE; // white box

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
        final ByteBuffer buffer = getSingleByteBufferWithExpectedBytes(1);
        assertThat(buffer.get(0), equalTo((byte) 0xc9));
    }

    @Test
    public void aFullBufferOfBytes() {
        final byte[] buf = new byte[BUFFER_SIZE];
        for (int i=0; i<BUFFER_SIZE; i++) {
            buf[i] = (byte) (i & 0xff);
        }
        encoder.writeBytes(buf);
        final ByteBuffer buffer = getSingleByteBufferWithExpectedBytes(BUFFER_SIZE);
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
        final List<ByteBuffer> buffers = encoder.getBuffers();
        assertThat(buffers, hasSize(2));

        final ByteBuffer firstBuffer = buffers.get(0);
        assertThat(firstBuffer.capacity(), equalTo(BUFFER_SIZE));
        assertThat(firstBuffer.limit(), equalTo(BUFFER_SIZE));
        final byte[] firstDst = new byte[BUFFER_SIZE];
        firstBuffer.get(firstDst);
        assertThat(firstDst, equalTo(Arrays.copyOf(buf, BUFFER_SIZE)));

        final ByteBuffer secondBuffer = buffers.get(1);
        assertThat(secondBuffer.capacity(), equalTo(BUFFER_SIZE));
        assertThat(secondBuffer.limit(), equalTo(128));
        final byte[] dst = new byte[128];
        secondBuffer.get(dst);
        assertThat(dst, equalTo(Arrays.copyOfRange(buf, BUFFER_SIZE, BUFFER_SIZE + 128)));
    }

    private ByteBuffer getSingleByteBufferWithExpectedBytes(final int expectedBytes) {
        final List<ByteBuffer> buffers = encoder.getBuffers();
        assertThat(buffers, hasSize(1));
        final ByteBuffer buffer = buffers.get(0);
        assertThat(buffer.capacity(), equalTo(BUFFER_SIZE));
        assertThat(buffer.limit(), equalTo(expectedBytes));
        return buffer;
    }

    @Test
    public void singleInt() {
        encoder.writeInt(0xabcdef01);
        final ByteBuffer buffer = getSingleByteBufferWithExpectedBytes(4);
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
        final ByteBuffer buffer = getSingleByteBufferWithExpectedBytes(2);
        assertThat(buffer.get(0), equalTo((byte) 0x00));
        assertThat(buffer.get(1), equalTo((byte) 0x01));
    }

    @Test
    public void singleChar() {
        encoder.writeChar('A');
        encoder.writeChar('0');
        encoder.writeChar('\uffff');
        final ByteBuffer buffer = getSingleByteBufferWithExpectedBytes(6);
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
        final ByteBuffer buffer = getSingleByteBufferWithExpectedBytes(2);
        assertThat(buffer.get(0), equalTo((byte) 0x87));
        assertThat(buffer.get(1), equalTo((byte) 0x64));
    }

    @Test
    public void singleLong() {
        encoder.writeLong(0x87643210ABCDEF10L);
        final ByteBuffer buffer = getSingleByteBufferWithExpectedBytes(8);
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
        final ByteBuffer buffer = getSingleByteBufferWithExpectedBytes(4);
        assertThat(buffer.get(0), equalTo((byte) 64));
        assertThat(buffer.get(1), equalTo((byte) 73));
        assertThat(buffer.get(2), equalTo((byte) 14));
        assertThat(buffer.get(3), equalTo((byte) 86));
    }

    @Test
    public void singleDouble() {
        encoder.writeDouble(3.1415);
        final int expectedBytes = 8;
        final ByteBuffer buffer = getSingleByteBufferWithExpectedBytes(expectedBytes);
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
        final ByteBuffer buffer = getSingleByteBufferWithExpectedBytes(4);
        assertThat(buffer.get(0), equalTo((byte) 0));
        assertThat(buffer.get(1), equalTo((byte) 0));
        assertThat(buffer.get(2), equalTo((byte) 0));
        assertThat(buffer.get(3), equalTo((byte) 0));
    }

    // TODO test the UTF8 encoding of strings

    @Test
    public void encodeString() {
        encoder.writeString("Hello");
        final ByteBuffer buffer = getSingleByteBufferWithExpectedBytes(9);
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
}
