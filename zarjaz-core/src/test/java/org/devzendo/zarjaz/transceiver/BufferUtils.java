package org.devzendo.zarjaz.transceiver;

import org.devzendo.zarjaz.nio.DefaultReadableByteBuffer;
import org.devzendo.zarjaz.nio.DefaultWritableByteBuffer;
import org.devzendo.zarjaz.nio.ReadableByteBuffer;
import org.devzendo.zarjaz.nio.WritableByteBuffer;

import java.nio.ByteBuffer;

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
public class BufferUtils {
    private static byte startByte = 0;

    public static ReadableByteBuffer createByteBuffer() {
        final int bufferSize = 10;
        final WritableByteBuffer bb = DefaultWritableByteBuffer.allocate(bufferSize);
        for (int i = 0; i < bufferSize; i++) {
            bb.put(startByte++);
        }
        return bb.flip();
    }

    public static ReadableByteBuffer duplicateOutgoingByteBuffer(final ReadableByteBuffer buffer) {
        final ByteBuffer duplicate = buffer.raw().duplicate();
        duplicate.rewind();
        return new DefaultReadableByteBuffer(duplicate);
    }
}
