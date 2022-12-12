#!/bin/bash
set -e
/opt/mssql-tools/bin/sqlcmd -S mssql.mssql.svc.cluster.local -U sa -P 'Password!42' -d master -i /init/setup.sql
