# Use an official Maven image with OpenJDK
FROM maven:3.9-eclipse-temurin-21

# Set the working directory in the container
WORKDIR /app

# Copy the current directory contents into the container at /app
COPY . /app

# Copy the SSL certificate into the container
#COPY mycert.crt /tmp/mycert.crt

# Build the project
#RUN mvn clean install

# Import the SSL certificate into the Java truststore
#RUN keytool -importcert -file /tmp/mycert.crt -alias my_cert_alias -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit -noprompt

# Clean up by removing the certificate file
#RUN rm /tmp/mycert.crt


# Build the project
RUN mvn clean install

# Run the application
CMD ["java", "-XX:+UseZGC", "-XX:+UseNUMA", "-XX:+AlwaysPreTouch", "-XX:+DisableExplicitGC", "-jar", "target/thoth-trading-1.0-SNAPSHOT-jar-with-dependencies.jar"]