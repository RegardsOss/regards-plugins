/*
 * Copyright 2017-2023 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
 *
 * This file is part of REGARDS.
 *
 * REGARDS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * REGARDS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with REGARDS. If not, see <http://www.gnu.org/licenses/>.
 */
package fr.cnes.regards.modules.storage.plugin.s3;

import fr.cnes.regards.framework.jpa.multitenant.lock.LockService;
import fr.cnes.regards.modules.storage.plugin.s3.utils.WaitingLock;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;

/**
 * Test for the {@link WaitingLock} class
 *
 * @author Thibaud Michaudel
 **/
@Ignore("Long test")
public class WaitingLockTest {

    @Test
    public void test_waiting_lock() throws InterruptedException {
        List<Long> renewList = new ArrayList<>();

        WaitingLock waitingLock = new WaitingLock("lock", Instant.now(), 2000, 10, mockLockService(renewList));
        waitingLock.waitAndRenew(5000);
        Assertions.assertEquals(2, renewList.size());
        Assertions.assertTrue(renewList.get(1) < renewList.get(0) + 2000 + 15
                              && renewList.get(1)
                                 > renewList.get(0)
                                   - 15); // Test that the second renew happened 2000 ms after the first one
    }

    @Test
    public void test_waiting_lock_long() throws InterruptedException {
        List<Long> renewList = new ArrayList<>();

        WaitingLock waitingLock = new WaitingLock("lock", Instant.now(), 1000, 10, mockLockService(renewList));
        waitingLock.waitAndRenew(15000);
        Assertions.assertEquals(15, renewList.size());
    }

    private LockService mockLockService(List<Long> renewsList) {
        LockService lockService = Mockito.mock(LockService.class);
        long start = Instant.now().toEpochMilli();
        Mockito.doAnswer((mock) -> {
            renewsList.add(Instant.now().toEpochMilli() - start);
            return null;
        }).when(lockService).renewLock(any());
        return lockService;
    }
}
