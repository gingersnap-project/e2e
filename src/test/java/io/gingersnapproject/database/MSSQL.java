package io.gingersnapproject.database;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MSSQL extends AbstractDatabase {

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
        props.put("username", "gingersnap_login");
    }

    @Override
    public String select(Set<String> valueColumns, String table, List<String> whereColumns) {
        return "SELECT " +
                String.join(", ", valueColumns) +
                " FROM " +
                table +
                " WHERE " +
                IntStream.range(0, whereColumns.size())
                        .mapToObj(index -> String.format("%s = @P%d", whereColumns.get(index), index + 1))
                        .collect(Collectors.joining(" AND "));
    }
}
