create database gingersnap;
go

use gingersnap;
go

EXEC sys.sp_cdc_enable_db;
go

create schema gingersnap;
go

create table gingersnap.customer(id int primary key, fullname varchar(255), email varchar(255));
go
create table gingersnap.car_model(id int primary key, model varchar(255), brand varchar(255));
go

EXEC sys.sp_cdc_enable_table @source_schema = N'gingersnap', @source_name = N'customer', @role_name = NULL, @supports_net_changes = 0
go
