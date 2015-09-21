/* Copyright 2015 Zürcher Hochschule der Angewandten Wissenschaften
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.zhaw.ficore.p2ds.group.storage;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.slf4j.LoggerFactory;
import org.slf4j.ext.XLogger;

public class Manager {

    private static EntityManager em;
    private static EntityManagerFactory entityManagerFactory;

    private static final XLogger LOGGER = new XLogger(
            LoggerFactory.getLogger(Manager.class));

    static {
        System.out.println("Manager static init!");
        try {
            entityManagerFactory = Persistence
                    .createEntityManagerFactory("p2ds-group-management");
            em = entityManagerFactory.createEntityManager();
        } catch (Exception ex) {
            LOGGER.catching(ex);
            ex.printStackTrace();
        }
    }

    public static EntityManager getEntityManager() {
        return em = entityManagerFactory.createEntityManager();
    }
}
