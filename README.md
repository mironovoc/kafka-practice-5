# Практическая работа 5

Debezium забирает изменения из PostgreSQL и кладёт их в Kafka. Таблицы `users` и `orders`. Метрики Connect смотрю в Prometheus и Grafana.

Брокер один, KRaft. В уроке их было три, но у служебных топиков Connect replication factor и так 1, для задания хватает. Порты другие, чтобы не пересечься с практикой 4.

Команды из папки `kafka-practice5`.

## Что где лежит

- `docker-compose.yml` - Kafka, Postgres, Connect, UI, Prometheus, Grafana
- `connector.json` - настройки Debezium
- `postgres/custom-config.conf` - `wal_level=logical`, без этого Debezium изменения не видит
- `postgres/init/01-tables.sql` - таблицы, создаются сами при первом старте базы
- `postgres/seed.sql` - тестовые вставки из задания
- `kafka-connect/` - образ Connect, внутрь положен Debezium и jmx-агент
- `CdcPrinter` - обычный consumer на `kafka-clients`, печатает оба топика. Spring не ставил

Как это связано: пишешь в Postgres, Debezium читает журнал WAL и шлёт строки в топики `customers.public.users` и `customers.public.orders`. Консьюмер читает эти топики. Connect отдельно отдаёт метрики на порт 9876, их забирает Prometheus, Grafana рисует графики.

Адреса с ноутбука:

- Kafka `127.0.0.1:19094`
- UI http://127.0.0.1:18085
- Connect http://127.0.0.1:18083
- Postgres `127.0.0.1:15432`, база `customers`, пользователь `postgres-user`, пароль `postgres-pw`
- метрики http://127.0.0.1:19876/metrics
- Prometheus http://127.0.0.1:19090
- Grafana http://127.0.0.1:13000, логин `admin`, пароль `admin`

## Настройки коннектора

Файл `connector.json`. Главное:

- хост базы `postgres` (имя сервиса в compose, не localhost), база `customers`
- `topic.prefix=customers`, поэтому топики называются `customers.public.users` и `customers.public.orders`
- `table.include.list` - только `public.users` и `public.orders`
- `plugin.name=pgoutput`, `snapshot.mode=initial` (сначала снимок таблиц, потом журнал)
- `ExtractNewRecordState` - в топик попадает сама строка, не конверт Debezium с `before`/`after`. Удаление приходит с полем `__deleted`

Слотов репликации поставил 4. В уроке было 1, но если коннектор пересоздать, старый слот ещё висит и Postgres новый не даёт.

## Запуск

1. Поднять стенд. Первый раз долго, качаются образы.

```
docker compose up -d --build
docker compose ps
```

Connect поднимается после брокера. Если REST сразу не отвечает, подожди и проверь лог:

```
docker logs p5-connect --tail 30
curl.exe -s http://127.0.0.1:18083
```

2. Таблицы. Скрипт из `postgres/init` уже отработал:

```
docker exec -i p5-postgres psql -U postgres-user -d customers -c "\dt"
```

Должны быть `users` и `orders`.

3. Зарегистрировать коннектор и проверить статус. В `curl` именно `curl.exe`, иначе PowerShell подсовывает свой `curl`.

```
curl.exe -s -X PUT -H "Content-Type: application/json" --data-binary "@connector.json" http://127.0.0.1:18083/connectors/pg-connector/config
curl.exe -s http://127.0.0.1:18083/connectors/pg-connector/status
```

И у коннектора, и у task 0 должно быть `RUNNING`. Если `FAILED` - текст ошибки в этом же ответе, подробности в `docker logs p5-connect`.

4. Вставить данные из задания:

```
Get-Content .\postgres\seed.sql | docker exec -i p5-postgres psql -U postgres-user -d customers
```

Повторно этот файл лучше не гонять, `id` уже заняты.

5. Прочитать топики:

```
.\gradlew.bat run
```

В консоли будут `customers.public.users` и `customers.public.orders`. `created_at` приходит числом, это микросекунды, так Debezium отдаёт timestamp. Остановить - Ctrl+C.

Группа называется `cdc-printer`. Если уже дочитала сообщения и молчит, сбрось offset и запусти снова:

```
docker exec p5-kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group cdc-printer --reset-offsets --to-earliest --all-topics --execute
```

Топики также видны в UI: http://127.0.0.1:18085

## Метрики

1. http://127.0.0.1:19876/metrics - в тексте есть `kafka_connect_`
2. http://127.0.0.1:19090/targets - job `kafka-connect-host` в состоянии UP
3. Grafana, дашборд Kafka Connect CDC. Логин `admin` / `admin`, смену пароля можно пропустить

На графиках статус задачи, Source Record Write rate (сколько записей ушло в Kafka) и Source Record Poll rate (сколько пришло из базы). На 4 пользователях и 5 заказах это короткий всплеск, не ровная линия: писать больше нечего.

## Остановка

```
docker compose down
```

С данными: `docker compose down -v`. После этого таблицы создаются заново, коннектор надо зарегистрировать ещё раз.
