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
package fr.cnes.regards.modules.storage.plugin.s3.utils;

import fr.cnes.regards.framework.jpa.multitenant.lock.LockService;

import java.time.Duration;
import java.time.Instant;

/**
 * Representation of a lock that have a creation time, a time to live and that can be renewed
 */
public class WaitingLock {

    private final String lockName;

    private Instant creationDate;

    private final long maxTimeToLive;

    private final LockService lockService;

    private final long renewCallDuration;

    public WaitingLock(String lockName,
                       Instant creationDate,
                       long maxTimeToLive,
                       long renewCallDuration,
                       LockService lockService) {
        this.lockName = lockName;
        this.creationDate = creationDate;
        this.maxTimeToLive = maxTimeToLive;
        this.lockService = lockService;
        this.renewCallDuration = renewCallDuration;
    }

    /**
     * Wait for the given duration and renew the lock when needed during the wait
     *
     * @param delay the time to wait
     * @throws InterruptedException
     */
    public void waitAndRenew(long delay) throws InterruptedException {
        long lockRemainingTime = getLockRemainingTime();
        long totalWaited = 0;
        while (totalWaited < delay) {
            long waitTime = Math.min(delay - totalWaited, lockRemainingTime);
            Thread.sleep(waitTime);
            lockRemainingTime -= waitTime;
            totalWaited += waitTime;
            if (lockRemainingTime <= 0) {
                renew();
                lockRemainingTime = getLockRemainingTime();
            }
        }
    }

    /**
     * Get the remaining time to live for the lock using the difference of the lock last renewal (or creation) date and now
     *
     * @return the remaining time to live in ms
     */
    private long getLockRemainingTime() {
        long currentTime = Instant.now().toEpochMilli();
        Instant expirationDate = creationDate.plusMillis(maxTimeToLive
                                                         - renewCallDuration); //Use an expiration date a bit earlier than the real one to account for renew time
        return Duration.between(Instant.ofEpochMilli(currentTime), expirationDate).toMillis();
    }

    private void renew() {
        lockService.renewLock(lockName);
        creationDate = Instant.now();
    }
}
