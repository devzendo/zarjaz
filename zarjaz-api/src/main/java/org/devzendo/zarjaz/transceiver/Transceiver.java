package org.devzendo.zarjaz.transceiver;

import org.devzendo.zarjaz.nio.ReadableByteBuffer;

import java.io.Closeable;
import java.io.IOException;
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
public interface Transceiver extends Closeable {
    public interface BufferWriter {
        void writeBuffer(List<ReadableByteBuffer> data) throws IOException;
    }

    public interface ObservableTransceiverEnd {
        void addTransceiverObserver(TransceiverObserver observer);
        void removeTransceiverObserver(TransceiverObserver observer);
    }

    void open() throws IOException;

    ObservableTransceiverEnd getClientEnd();
    ObservableTransceiverEnd getServerEnd();
    BufferWriter getServerWriter();

    boolean supportsMultipleReturn();
}
