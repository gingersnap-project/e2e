package io.gingersnapproject;

import io.gingersnap_project.v1alpha1.cachespec.DataSource;
import io.gingersnapproject.database.MSSQL;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("mssql")
@ExtendWith(MSSQL.class)
public class MSSQLLazyCacheTest extends AbstractLazyCacheTest {
    public MSSQLLazyCacheTest() {
        super(DataSource.DbType.SQL_SERVER_2019, MSSQL.PLACEHOLDER);
    }
}
