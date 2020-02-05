package org.breedinginsight.daos;

import org.breedinginsight.dao.db.tables.daos.BiUserDao;
import org.jooq.Configuration;

import javax.inject.Inject;
import javax.inject.Singleton;

// need annotation to find bean in micronaut test context
@Singleton
public class UserDao extends BiUserDao {
    @Inject
    public UserDao(Configuration config) {
        super(config);
    }
}
