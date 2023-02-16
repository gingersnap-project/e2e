package io.gingersnapproject.database;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MySQL extends AbstractDatabase {

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

    @Override
    public String select(Set<String> valueColumns, String table, List<String> whereColumns) {
        return "SELECT " +
                String.join(", ", valueColumns) +
                " FROM " +
                table +
                " WHERE " +
                whereColumns.stream()
                        .map(whereColumn -> whereColumn + " = ?")
                        .collect(Collectors.joining(" AND "));
    }
}
