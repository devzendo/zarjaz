package org.devzendo.zarjaz.protocol;

import org.devzendo.zarjaz.nio.DefaultWritableByteBuffer;
import org.devzendo.zarjaz.nio.ReadableByteBuffer;
import org.devzendo.zarjaz.nio.WritableByteBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.singletonList;
import static org.devzendo.zarjaz.protocol.SampleInterfaces.parameterType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
public class TestByteBufferDecoder {
    private static final int BUFFER_SIZE = Protocol.BUFFER_SIZE; // white box

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void emptinessFromEmptyList() {
        final ByteBufferDecoder decoder = decoder(EMPTY_LIST);
        assertThat(decoder.empty(), is(true));
        assertThat(decoder.size(), is(0));
    }

    @Test
    public void emptinessFromListWithEmptyBuffer() {
        final WritableByteBuffer buffer = allocateBuffer();
        final ReadableByteBuffer readableByteBuffer = buffer.flip();

        final ByteBufferDecoder decoder = decoder(singletonList(readableByteBuffer));
        assertThat(decoder.empty(), is(true));
        assertThat(decoder.size(), is(0));
    }

    @Test
    public void singleByte() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeByte((byte) 0xc9);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.empty(), is(false));
        assertThat(decoder.size(), is(1));
        assertThat(decoder.readByte(), is((byte) 0xc9));
    }

    @Test
    public void readByteThrowsIfEmpty() throws IOException {
        thrown.expect(IOException.class);

        final ByteBufferDecoder decoder = decoder(EMPTY_LIST);
        decoder.readByte();
    }

    @Test
    public void readByteThrowsIfEmptyBuffer() throws IOException {
        thrown.expect(IOException.class);

        final WritableByteBuffer buffer = allocateBuffer();
        final ReadableByteBuffer readableByteBuffer = buffer.flip();
        final ByteBufferDecoder decoder = decoder(singletonList(readableByteBuffer));
        decoder.readByte();
    }

    @Test
    public void byteArrayStraddlingBufferBoundaries() throws IOException {
        final WritableByteBuffer first = DefaultWritableByteBuffer.allocate(2);
        first.put((byte) 0x01);
        first.put((byte) 0x02);
        final WritableByteBuffer second = DefaultWritableByteBuffer.allocate(2);
        second.put((byte) 0x03);
        second.put((byte) 0x04);

        final ByteBufferDecoder decoder = decoder(Arrays.asList(first.flip(), second.flip()));
        assertThat(decoder.empty(), is(false));
        assertThat(decoder.size(), is(4));
        final byte[] dest = new byte[4];
        decoder.readBytes(dest, 4);
        final byte[] expected = new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04 };
        assertThat(dest, is(expected));
    }

    @Test
    public void exhaustionReadingByteArray() throws IOException {
        thrown.expect(IOException.class);

        final WritableByteBuffer first = DefaultWritableByteBuffer.allocate(2);
        first.put((byte) 0x01);
        first.put((byte) 0x02);

        final ByteBufferDecoder decoder = decoder(Arrays.asList(first.flip()));
        assertThat(decoder.empty(), is(false));
        assertThat(decoder.size(), is(2));
        decoder.readBytes(new byte[4], 4);
    }

    @Test
    public void singleInt() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeInt(0x12345678);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.empty(), is(false));
        assertThat(decoder.size(), is(4));
        assertThat(decoder.readInt(), is(0x12345678));
    }

    @Test
    public void singleLong() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeLong(0x1234567812345678L);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.empty(), is(false));
        assertThat(decoder.size(), is(8));
        assertThat(decoder.readLong(), is(0x1234567812345678L));
    }

    @Test
    public void singleNegativeLong() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeLong(-1L);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.empty(), is(false));
        assertThat(decoder.size(), is(8));
        assertThat(decoder.readLong(), is(0xffffffffffffffffL));
    }

    @Test
    public void singleBoolean() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeBoolean(true);
        encoder.writeBoolean(false);
        encoder.writeBoolean(true);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.empty(), is(false));
        assertThat(decoder.size(), is(3));
        assertThat(decoder.readBoolean(), is(true));
        assertThat(decoder.readBoolean(), is(false));
        assertThat(decoder.readBoolean(), is(true));
    }

    @Test
    public void singleChar() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeChar('Q');

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.empty(), is(false));
        assertThat(decoder.size(), is(2));
        assertThat(decoder.readChar(), is('Q'));
    }

    @Test
    public void singleShort() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeShort((short) 0x1234);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.empty(), is(false));
        assertThat(decoder.size(), is(2));
        assertThat(decoder.readShort(), is((short) 0x1234));
    }

    @Test
    public void singleFloat() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeFloat(3.1415f);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.empty(), is(false));
        assertThat(decoder.size(), is(4));
        assertThat(decoder.readFloat(), is(3.1415f));
    }

    @Test
    public void singleDouble() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeDouble(3.1415);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.empty(), is(false));
        assertThat(decoder.size(), is(8));
        assertThat(decoder.readDouble(), is(3.1415));
    }

    @Test
    public void singleString() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeString("UTF8");

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.empty(), is(false));
        assertThat(decoder.size(), is(8));
        assertThat(decoder.readString(), is("UTF8"));
    }

    @Test
    public void decodeObjectBytePrimitive() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeByte((byte) 0xc9);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.BytePrimitiveInterface.class)), is((byte) 0xc9));
    }

    @Test
    public void decodeObjectByteWrapper() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeByte((byte) 0xc9);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.ByteWrapperInterface.class)), is((byte) 0xc9));
    }

    @Test
    public void decodeObjectShortPrimitive() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeShort((short) 0x1234);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.ShortPrimitiveInterface.class)), is((short) 0x1234));
    }

    @Test
    public void decodeObjectShortWrapper() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeShort((short) 0x1234);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.ShortWrapperInterface.class)), is((short) 0x1234));
    }

    @Test
    public void decodeObjectCharPrimitive() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeChar((char) 'a');

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.CharPrimitiveInterface.class)), is((char) 'a'));
    }

    @Test
    public void decodeObjectCharWrapper() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeChar((char) 'a');

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.CharWrapperInterface.class)), is((char) 'a'));
    }

    @Test
    public void decodeObjectFloatPrimitive() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeFloat((float) 3.1415f);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.FloatPrimitiveInterface.class)), is((float) 3.1415f));
    }

    @Test
    public void decodeObjectFloatWrapper() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeFloat((float) 3.1415f);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.FloatWrapperInterface.class)), is((float) 3.1415f));
    }

    @Test
    public void decodeObjectDoublePrimitive() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeDouble((double) 3.1415d);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.DoublePrimitiveInterface.class)), is((double) 3.1415d));
    }

    @Test
    public void decodeObjectDoubleWrapper() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeDouble((double) 3.1415d);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.DoubleWrapperInterface.class)), is((Double) 3.1415d));
    }
    @Test
    public void decodeObjectIntPrimitive() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeInt(0x12345678);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.IntPrimitiveInterface.class)), is(0x12345678));
    }

    @Test
    public void decodeObjectIntWrapper() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeInt(0x12345678);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.IntWrapperInterface.class)), is(0x12345678));
    }

    @Test
    public void decodeObjectLongPrimitive() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeLong(0x0102030405060708L);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.LongPrimitiveInterface.class)), is(0x0102030405060708L));
    }

    @Test
    public void decodeObjectLongWrapper() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeLong(0x0102030405060708L);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.LongWrapperInterface.class)), is(0x0102030405060708L));
    }

    @Test
    public void decodeObjectBooleanPrimitive() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeBoolean(true);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.BooleanPrimitiveInterface.class)), is(true));
    }

    @Test
    public void decodeObjectBooleanWrapper() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeBoolean(true);

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.BooleanWrapperInterface.class)), is(true));
    }

    @Test
    public void decodeObjectStringWrapper() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeString("test it!");

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.readObject(parameterType(SampleInterfaces.StringInterface.class)), is("test it!"));
    }

    private WritableByteBuffer allocateBuffer() {
        return DefaultWritableByteBuffer.allocate(Protocol.BUFFER_SIZE);
    }

    private ByteBufferDecoder decoder(final List<ReadableByteBuffer> buffers) {
        return new ByteBufferDecoder(buffers);
    }
}
