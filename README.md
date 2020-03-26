# BI API

The BI API powers the BI Web, as well as BrAPI powered applications.

The API is built using Java 12 and the Micronaut framework.  The development guide for Micronaut can be found at: https://docs.micronaut.io/latest/guide/index.html

## Docker support
The API can run inside a Docker container using the Dockerfile for building the
API image and the `docker-compose.yml` to run the container.
```
docker-compose up -d biapi-dev
```

The `docker-compose.yml` should contain a service for each environment the API is
to be run in: e.g develop, test, staging, and production.  Each service contains
under the environment key public values for environment variables used as params
for the API configuration.

Private values used in each environment are stored in Lastpass and are never
placed in docker-compose.yml and never committed to the repo.  At the root level
of the repo locally create a file called .env and save the Lastpass contents for
"bi-api secrets" in this file.

### Running tests in docker container

If you have the docker socket (/var/run/docker.sock) and docker executable folder (/usr/bin/docker)
mounted in your docker-compose.yml file, you will be able to run the biapi tests from within the docker container. 
The default docker-compose file has these mounted. 

The biapi tests use the hosts docker instance to spin up test database containers for integration tests. 

NOTE: Do not run a production container with the docker socket mounted. While useful for testing and
development, mounting the docker socket into a container is a large security risk. The application will
still work fine without the docker socket mounted, but tests will fail. 

To run the tests, use the following command:

```docker exec -it biapi mvn test```

## Pull Request Criteria

When evaluating a pull request for merge acceptance, verify that the following criteria are met:

* Any changes to API code have a corresponding change to the openAPI specification in the `docs` folder
* Any changes to the API code have corresponding endpoint tests

## Development Guide
The following sections provide instructions to get the application running in your local development environment. 
This section is provided as an alternative to running the application in Docker. 

### Prerequisites

1. Java 13 SDK installed
1. Maven installed (or via IDE)

### Project setup
The micronaut-security code is included as a git submodule.  To include the contents of this submodule use the --recurse-submodules flag when cloning the bi-api repo:

```
git clone --recurse-submodules https://<user>@bitbucket.org/breedinginsight/bi-api.git
```

#### Postgres

Run this docker command in terminal to start up a postgres docker container with the empty bi_db database in it. 

```
docker container run --name bidb -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=bidb -p 5432:5432 -d postgis/postgis:12-3.0
```

### Build the app
In `src/build/`, make a copy of `db.config.properties` as `db.config.dev.properties` (this file is ignored from git) and replace placeholder values.

After that, run:

```
mvn clean validate flyway:clean flyway:migrate install -P dev
```

This process will pull down all the application's dependencies to your local machine, create the application's database structure (with appropriate data), generate required source files (via JOOQ), run unit tests, and finally build the final JAR file. 

### Run the app

- You will need to create your user in the database table in order to log in successfully. Execute the following line in the sql executor of your choice (don't forget to populate the `values` of the query): 

```sql
insert into bi_user (orcid, name) values ('xxxx-xxxx-xxxx-xxxx', '<name>');
``` 

- Create a application variable configuration file for the environment you plan to run the project under (```Ex.  application-dev.yml```).
You can reference the `src/build/makeApplication-dev.yml.sh` file to see what information is needed in the
variable configuration.

*If running in an IDE (like IntelliJ)*:

- Create an application run config with the main class being `org.breedinginsight.api.Application`
- Pass VM options of: `--enable-preview --illegal-access=warn -Dmicronaut.environments=dev`

*If running as a packaged JAR*:

```
java --enable-preview -Dmicronaut.environments=dev -jar bi-api*.jar
```


### Ongoing Development

#### Updating the database

If you have run the project and the database once already, you will need to make sure your database is up to date by running: 

```
mvn validate flyway:migrate -X -P dev
```


If you don't care about losing any of the data in the database, run:

```
mvn validate flyway:clean flyway:migrate -X -P dev
```

### Generating Java Classes via JOOQ

As structural database changes are made, you will need to re-generate Java classes via JOOQ (data model, base DAOs).  To do so, run:

```
mvn clean generate-sources -P dev
```

### Testing

#### Test database

The test database is started as part of the tests in a separate docker container. The JOOQ classes for the tests
are generated from the main database, so this will have to be up and running. The reference to the main database
can be found in src/build/build.config.properties. 

### Run tests

Create a file, `application-test.yml` in the `src/test/resources` directory.
You can reference the `src/build/makeApplication-dev.yml.sh` file to see what information is needed in the
variable configuration.

Tests can be run with the following command:

```
mvn test
```

They are also run as part of the install profile. In IntelliJ you can create test profiles for the tests to get easily readable output.

### Troubleshooting

If you are having errors to the effect of `invalid source release 12 with --enable-preview` and are using IntelliJ, change the jdk to 13 in the following places and it may help:

1. File -> Project Structure -> Project Settings
2. File -> Project Structure -> Modules -> Tab: Sources: Language Level
3. File -> Project Structure -> Modules -> Tab: Dependencies: Module SDK
4. Main -> Preferences -> Compiler -> Java Compiler -> Target bytecode version
5. Run/Debug Configurations (Your run profiles) -> JRE select box