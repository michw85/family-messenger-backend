@"
-- Настройка pg_hba для всех подключений
ALTER SYSTEM SET listen_addresses = '*';

-- Подождать инициализации
SELECT 1;
"@ | Out-File -FilePath "./postgres-init/init.sql" -Encoding utf8