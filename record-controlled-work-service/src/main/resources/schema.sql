DROP TABLE IF EXISTS ITEMS;
-- fixing for integration tests for setup purposes.
CREATE TABLE ITEMS
(
    id          BIGINT          AUTO_INCREMENT,
    name        varchar(20)     NOT NULL,
    description varchar(100)    NOT NULL,
    PRIMARY KEY (id)
);