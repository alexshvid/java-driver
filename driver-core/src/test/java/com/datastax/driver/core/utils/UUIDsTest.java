/*
 *      Copyright (C) 2012 DataStax Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.datastax.driver.core.utils;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.cassandra.db.marshal.TimeUUIDType;

public class UUIDsTest {

    @Test
    public void conformanceTest() {

        // The UUIDs class does some computation at class initialization, which
        // may screw up our assumption below that UUIDs.timeBased() takes less
        // than 10ms, so force class loading now.
        UUIDs.random();

        long now = System.currentTimeMillis();
        UUID uuid = UUIDs.timeBased();

        assertEquals(1, uuid.version());
        assertEquals(2, uuid.variant());

        long tstamp = UUIDs.unixTimestamp(uuid);

        // Check now and the uuid timestamp are within 10 millisseconds.
        assert now <= tstamp && now >= tstamp - 10 : String.format("now = %d, tstamp = %d", now, tstamp);
    }

    @Test
    public void uniquenessTest() {
        // Generate 1M uuid and check we never have twice the same one

        int nbGenerated = 1000000;
        Set<UUID> generated = new HashSet<UUID>(nbGenerated);

        for (int i = 0; i < nbGenerated; ++i)
            generated.add(UUIDs.timeBased());

        assertEquals(nbGenerated, generated.size());
    }

    @Test
    public void multiThreadUniquenessTest() throws Exception {
        int nbThread = 10;
        int nbGenerated = 500000;
        Set<UUID> generated = new ConcurrentSkipListSet<UUID>();

        UUIDGenerator[] generators = new UUIDGenerator[nbThread];
        for (int i = 0; i < nbThread; i++)
            generators[i] = new UUIDGenerator(nbGenerated, generated);

        for (int i = 0; i < nbThread; i++)
            generators[i].start();

        for (int i = 0; i < nbThread; i++)
            generators[i].join();

        assertEquals(nbThread * nbGenerated, generated.size());
    }

    @Test
    public void timestampIncreasingTest() {
        // Generate 1M uuid and check timestamp are always increasing
        int nbGenerated = 1000000;
        long previous = 0;

        for (int i = 0; i < nbGenerated; ++i) {
            long current = UUIDs.timeBased().timestamp();
            assert previous < current : String.format("previous = %d >= %d = current", previous, current);
        }
    }

    @Test
    public void startEndOfTest() {

        Random random = new Random(System.currentTimeMillis());

        int nbTstamp = 10;
        int nbPerTstamp = 10;

        for (int i = 0; i < nbTstamp; i++) {
            long tstamp = (long)random.nextInt();
            for (int j = 0; j < nbPerTstamp; j++) {
                assertWithin(new UUID(UUIDs.makeMSB(UUIDs.fromUnixTimestamp(tstamp)), random.nextLong()), UUIDs.startOf(tstamp), UUIDs.endOf(tstamp));
            }
        }
    }

    private static void assertWithin(UUID uuid, UUID lowerBound, UUID upperBound) {
        ByteBuffer uuidBytes = TimeUUIDType.instance.decompose(uuid);
        ByteBuffer lb = TimeUUIDType.instance.decompose(lowerBound);
        ByteBuffer ub = TimeUUIDType.instance.decompose(upperBound);
        assertTrue(TimeUUIDType.instance.compare(lb, uuidBytes) <= 0);
        assertTrue(TimeUUIDType.instance.compare(ub, uuidBytes) >= 0);
    }

    private static class UUIDGenerator extends Thread {

        private final int toGenerate;
        private final Set<UUID> generated;

        UUIDGenerator(int toGenerate, Set<UUID> generated) {
            this.toGenerate = toGenerate;
            this.generated = generated;
        }

        public void run() {
            for (int i = 0; i < toGenerate; ++i)
                generated.add(UUIDs.timeBased());
        }
    }
}
