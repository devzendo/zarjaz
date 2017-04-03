package org.devzendo.zarjaz.nio;

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
public class DefaultWritableByteBuffer extends AbstractPhantomByteBuffer implements WritableByteBuffer {

    public static WritableByteBuffer allocate(int capacity) {
        return new DefaultWritableByteBuffer(ByteBuffer.allocate(capacity));
    }

    public DefaultWritableByteBuffer(final ByteBuffer data) {
        super(data);
    }

    @Override
    public ReadableByteBuffer flip() {
        super.flipData();
        return new DefaultReadableByteBuffer(data);
    }
    public void put(final byte b) {
        assertNotFlipped();
        data.put(b);
    }

    @Override
    public void put(final byte[] bs, final int offset, final int remaining) {
        assertNotFlipped();
        data.put(bs, offset, remaining);
    }

    @Override
    public void put(byte[] bs) {
        assertNotFlipped();
        data.put(bs);
    }

    @Override
    public void putInt(final int i) {
        assertNotFlipped();
        data.putInt(i);
    }

    @Override
    public void put(final ReadableByteBuffer readableByteBuffer) {
        assertNotFlipped();
        data.put(readableByteBuffer.raw());
    }

    @Override
    public void clear() {
        data.clear(); // TODO test around this behaviour
        flipped = false;
    }

    @Override
    public ByteBuffer raw() {
        return data;
    }

    @Override
    public void rewind() {
        data.rewind();
    }
}
