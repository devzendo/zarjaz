package org.devzendo.zarjaz.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
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
    private final List<ByteBuffer> buffers;
    private int currentBuffer = 0;

    public ByteBufferDecoder(final List<ByteBuffer> buffers) {
        this.buffers = buffers;
    }

    public boolean empty() {
        final ByteBuffer buffer = getBuffer();
        return buffer == null || buffer.remaining() == 0;
    }

    public int size() {
        int size = 0;
        for (ByteBuffer buffer : buffers) {
            size += buffer.remaining();
        }
        return size;
    }

    private ByteBuffer getBuffer() {
        while (currentBuffer < buffers.size()) {
            ByteBuffer buffer = buffers.get(currentBuffer);
            if (buffer.hasRemaining())
                return buffer;
            currentBuffer++;
        }
        return null;
    }

    public byte readByte() throws IOException {
        final ByteBuffer buffer = getBuffer();
        if (buffer == null || buffer.remaining() == 0) {
            exhausted(1);
        }
        return buffer.get();
    }

    private void exhausted(final int requiredBytes) throws IOException {
        throw new IOException("Buffers exhausted; " + requiredBytes + " byte(s) required");
    }
}
