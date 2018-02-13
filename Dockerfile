FROM jboss/keycloak:3.4.3.Final

MAINTAINER Robert Brem <brem_robert@hotmail.com>

ADD target/event-listener-jar-with-dependencies.jar /opt/jboss/keycloak/providers/event-listener.jar

CMD [ "-b", "0.0.0.0", "-Dkeycloak.migration.action=import", "-Dkeycloak.migration.provider=singleFile", "-Dkeycloak.migration.file=/opt/jboss/import/keycloak-export.json", "-Dkeycloak.migration.strategy=OVERWRITE_EXISTING" ]