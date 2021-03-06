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
public interface WritableByteBuffer {
    ReadableByteBuffer flip();

    int remaining();

    int limit();

    void put(byte b);

    void put(byte[] bs, int offset, int remaining);

    void put(byte[] bs);

    void putInt(int i);

    void put(ReadableByteBuffer readableByteBuffer);

    void clear();

    ByteBuffer raw();

    void rewind();

}
