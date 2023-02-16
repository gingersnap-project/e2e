create login gingersnap_login with password = 'Password!42';
go

grant VIEW SERVER STATE to gingersnap_login;
go

create database gingersnap;
go

use gingersnap;
go

create schema gingersnap;
go

create user gingersnap_user for login gingersnap_login;
go

alter role db_owner add member gingersnap_user;
go

EXEC sp_addsrvrolemember 'gingersnap_login', 'sysadmin'
go

create table gingersnap.customer(id int primary key, fullname varchar(255), email varchar(255));
go

EXEC sys.sp_cdc_enable_db;
go

EXEC sys.sp_cdc_enable_table @source_schema = N'gingersnap', @source_name = N'customer', @role_name = NULL, @supports_net_changes = 0;
go

EXEC sys.sp_cdc_help_change_data_capture;
go
