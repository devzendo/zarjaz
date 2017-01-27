package org.devzendo.zarjaz.protocol;

import org.junit.Test;

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

    @Test
    public void emptyBuffers() {
        assertThat(encoder.getBuffers(), empty());
    }

    @Test
    public void singleByte() {
        encoder.writeByte((byte) 0xc9);
        final List<ByteBuffer> buffers = encoder.getBuffers();
        assertThat(buffers, hasSize(1));
        final ByteBuffer buffer = buffers.get(0);
        assertThat(buffer.capacity(), equalTo(BUFFER_SIZE));
        assertThat(buffer.limit(), equalTo(1));
        assertThat(buffer.get(0), equalTo((byte) 0xc9));
    }

    @Test
    public void aFullBufferOfBytes() {
        final byte[] buf = new byte[BUFFER_SIZE];
        for (int i=0; i<BUFFER_SIZE; i++) {
            buf[i] = (byte) (i & 0xff);
        }
        encoder.writeBytes(buf);
        final List<ByteBuffer> buffers = encoder.getBuffers();
        assertThat(buffers, hasSize(1));
        final ByteBuffer buffer = buffers.get(0);
        assertThat(buffer.capacity(), equalTo(BUFFER_SIZE));
        assertThat(buffer.limit(), equalTo(BUFFER_SIZE));
        final byte[] dst = new byte[BUFFER_SIZE];
        buffer.get(dst);
        assertThat(dst, equalTo(buf));
    }

    @Test
    public void moreThanOneBufferOfBytes() {
        final byte[] buf = new byte[BUFFER_SIZE + 128];
        for (int i=0; i<BUFFER_SIZE + 128; i++) {
            buf[i] = (byte) (i & 0xff);
        }
        encoder.writeBytes(buf);
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
}
