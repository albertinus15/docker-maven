FROM tomcat:9-jdk17-temurin-focal

COPY ./target/*.war /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080

CMD ["catalina.sh", "run"]