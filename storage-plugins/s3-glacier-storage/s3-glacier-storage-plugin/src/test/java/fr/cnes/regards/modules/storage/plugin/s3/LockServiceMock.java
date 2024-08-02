/*
 * Copyright 2017-2024 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
import fr.cnes.regards.framework.jpa.multitenant.lock.LockServiceResponse;
import fr.cnes.regards.framework.jpa.multitenant.lock.LockServiceTask;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author Sébastien Binda
 **/
public class LockServiceMock extends LockService {

    private static final Logger LOGGER = getLogger(LockServiceMock.class);

    private final Map<String, String> lockAcquired = new HashMap<>();

    private final Map<String, String> waitingLock = new HashMap<>();

    @Override
    public <T> LockServiceResponse<T> runWithLock(String lockName, LockServiceTask process) {
        if (isLockAcquired(lockName)) {
            waitingLock.put(lockName, process.getClass().getName());
            LOGGER.info("Action waiting for lock {} for process {}", lockName, process.getClass().getName());
        } else {
            lockAcquired.put(lockName, process.getClass().getName());
            LOGGER.info("Running action with lock {} for process {}", lockName, process.getClass().getName());
        }

        return new LockServiceResponse(true, false);
    }

    @Override
    public <T> LockServiceResponse<T> tryRunWithLock(String lockName,
                                                     LockServiceTask<T> process,
                                                     int timeToWait,
                                                     TimeUnit timeUnit) throws InterruptedException {
        return runWithLock(lockName, process);
    }

    public void reset() {
        lockAcquired.clear();
        waitingLock.clear();
    }

    public Map<String, String> getLockAcquired() {
        return lockAcquired;
    }

    public Map<String, String> getWaitingLock() {
        return waitingLock;
    }

    public boolean isLockAcquired(String lockName) {
        return lockAcquired.containsKey(lockName);
    }

}
