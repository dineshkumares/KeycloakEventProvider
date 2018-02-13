FROM jboss/keycloak:3.4.3.Final

MAINTAINER Robert Brem <brem_robert@hotmail.com>

ADD target/event-listener-jar-with-dependencies.jar /opt/jboss/keycloak/providers/event-listener.jar
