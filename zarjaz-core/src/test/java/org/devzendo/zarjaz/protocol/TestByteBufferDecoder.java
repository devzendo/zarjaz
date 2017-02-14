package org.devzendo.zarjaz.protocol;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.singletonList;
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
        final ByteBuffer buffer = allocateBuffer();
        buffer.flip();

        final ByteBufferDecoder decoder = decoder(singletonList(buffer));
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

        final ByteBuffer buffer = allocateBuffer();
        buffer.flip();
        final ByteBufferDecoder decoder = decoder(singletonList(buffer));
        decoder.readByte();
    }

    @Test
    public void byteArrayStraddlingBufferBoundaries() throws IOException {
        final ByteBuffer first = ByteBuffer.allocate(2);
        first.put((byte) 0x01);
        first.put((byte) 0x02);
        first.flip();
        final ByteBuffer second = ByteBuffer.allocate(2);
        second.put((byte) 0x03);
        second.put((byte) 0x04);
        second.flip();

        final ByteBufferDecoder decoder = decoder(Arrays.asList(first, second));
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

        final ByteBuffer first = ByteBuffer.allocate(2);
        first.put((byte) 0x01);
        first.put((byte) 0x02);
        first.flip();

        final ByteBufferDecoder decoder = decoder(Arrays.asList(first));
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
    public void singleString() throws IOException {
        final ByteBufferEncoder encoder = new ByteBufferEncoder();
        encoder.writeString("UTF8");

        final ByteBufferDecoder decoder = decoder(encoder.getBuffers());
        assertThat(decoder.empty(), is(false));
        assertThat(decoder.size(), is(8));
        assertThat(decoder.readString(), is("UTF8"));
    }

    private ByteBuffer allocateBuffer() {
        return ByteBuffer.allocate(Protocol.BUFFER_SIZE);
    }

    private ByteBufferDecoder decoder(final List<ByteBuffer> buffers) {
        return new ByteBufferDecoder(buffers);
    }
}
