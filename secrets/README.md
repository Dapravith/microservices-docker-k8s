# Secrets

These files are mounted as Docker secrets at `/run/secrets/<name>` inside each
service container. Spring Boot reads them via `spring.config.import` in
`application.yml`, so the values never appear as environment variables and are
not visible to `docker inspect`.

| File                  | Mount path                       | Used by                                 |
|-----------------------|----------------------------------|-----------------------------------------|
| `mongo.properties`    | `/run/secrets/mongo.properties`  | registration, login, student, teacher   |
| `jwt.properties`      | `/run/secrets/jwt.properties`    | login, gateway                          |

The `.example` files are committed templates. The real `*.properties` files
are gitignored — copy the example, fill in real values:

```sh
cp secrets/mongo.properties.example secrets/mongo.properties
cp secrets/jwt.properties.example   secrets/jwt.properties
```

## Why files instead of `.env`

Anything in `environment:` ends up visible to `docker inspect <container>`,
container-side `env`, child processes, and any error/crash dump that prints
the environment. File-mounted secrets stay in `/run/secrets/` and are only
read by the JVM at startup.
