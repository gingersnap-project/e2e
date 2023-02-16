create table gingersnap.customer(id int not null, fullname varchar(255), email varchar(255), constraint primary key (id));
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'gingersnap_user';
