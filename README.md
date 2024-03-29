# opencookbook-apiserver

![Sonar Quality Gate](https://img.shields.io/sonar/quality_gate/steve192_opencookbook-apiserver/main?server=https%3A%2F%2Fsonarcloud.io)
![Sonar Test Success Rate](https://img.shields.io/sonar/test_success_density/steve192_opencookbook-apiserver/main?server=https%3A%2F%2Fsonarcloud.io)

This is the backend component of the opencookbook.

It is a java application based on spring boot. It is used to manage user accounts and all data saved by users.

For the entrypoint of this whole project visit https://github.com/steve192/opencookbook


## Running locally
- Start a postgres db with
```
docker run -it --rm -p 5432:5432 -e POSTGRES_USER=cookpal -e POSTGRES_DB=cookpal -e POSTGRES_PASSWORD=changeme postgres:16-alpine
```