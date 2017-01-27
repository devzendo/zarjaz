package org.devzendo.zarjaz.protocol;

import org.junit.Test;

import java.nio.ByteBuffer;
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
        assertThat(buffer.capacity(), equalTo(ByteBufferEncoder.BUFFER_SIZE)); // white box
        assertThat(buffer.limit(), equalTo(1));
        assertThat(buffer.get(0), equalTo((byte) 0xc9));
    }
}
