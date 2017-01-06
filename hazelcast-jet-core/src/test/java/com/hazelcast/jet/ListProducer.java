/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet;

import com.hazelcast.jet.impl.connector.AbstractProducer;

import java.util.Iterator;
import java.util.List;

class ListProducer extends AbstractProducer {

    private Iterator<?> iterator;
    private int batchSize;
    private boolean completed;

    public ListProducer(List<?> list, int batchSize) {
        this.iterator = list.iterator();
        this.batchSize = batchSize;
    }

    @Override
    public boolean complete() {
        if (completed) {
            throw new IllegalStateException("process() called after completion");
        }
        for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
            emit(iterator.next());
        }
        completed = !iterator.hasNext();
        return completed;
    }
}
