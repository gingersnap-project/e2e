package io.gingersnapproject.database;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class MSSQL extends AbstractDatabase {
    public static final String PLACEHOLDER = "@P1";

    public MSSQL() {
        super("mssql", 1433);
    }

    @Override
    protected DBInitializer initializer() {
        return new DBInitializer(
                "mcr.microsoft.com/mssql-tools",
                List.of("/bin/sh", "-c", "/init/setup.sh"),
                Set.of("setup.sh", "setup.sql")
        );
    }

    @Override
    protected void connectionProperties(Map<String, String> props) {
        props.put("username", "sa");
    }
}
