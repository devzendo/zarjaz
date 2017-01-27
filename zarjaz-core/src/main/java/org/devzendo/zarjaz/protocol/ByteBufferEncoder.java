package org.devzendo.zarjaz.protocol;

import java.nio.Buffer;
import java.nio.ByteBuffer;
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
    static final int BUFFER_SIZE = 8192;

    private final LinkedList<ByteBuffer> buffers = new LinkedList<>();

    public List<ByteBuffer> getBuffers() {
        buffers.forEach(Buffer::flip);
        // TODO reset?
        return Collections.unmodifiableList(buffers);
    }

    public void writeByte(final byte b) {
        getCurrentBuffer().put(b);
    }

    private ByteBuffer getCurrentBuffer() {
        if (buffers.size() == 0) {
            addBuffer();
        }
        final ByteBuffer last = buffers.getLast();
        // TODO need to check for exhaustion, and allocate another
        return last;
    }

    private void addBuffer() {
        buffers.add(ByteBuffer.allocate(BUFFER_SIZE));
    }


    public void writeInt(final int i) {

    }

    public void writeBytes(final byte[] bs) {
        getCurrentBuffer().put(bs);
    }
}
