-- Business table owned by the test application, created the way an application would create its
-- own schema. Proves that the library's tables and the application's tables coexist.
CREATE TABLE IF NOT EXISTS test_orders
(
    id     UUID        NOT NULL PRIMARY KEY,
    status VARCHAR(20) NOT NULL
);
