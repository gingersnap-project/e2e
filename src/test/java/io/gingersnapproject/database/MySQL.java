package io.gingersnapproject.database;

import java.util.List;
import java.util.Set;

public class MySQL extends AbstractDatabase {

    public static final String PLACEHOLDER = "?";

    public MySQL() {
        super("mysql", 3306);
    }

    @Override
    protected DBInitializer initializer() {
        return new DBInitializer(
                "mysql:8.0.31",
                List.of(
                        "/bin/sh",
                        "-c",
                        String.format("mysql -h %s --user=root --password=root < /init/setup.sql", host())
                ),
                Set.of("setup.sql")
        );
    }
}
