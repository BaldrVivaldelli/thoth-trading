# Variables
DOCKER_IMAGE = thoth-trading
DOCKERFILE = Dockerfile
CERT_FILE = mycert.crt
ALIAS = my_cert_alias

# Targets
.PHONY: all build run clean

all: build

# Build the Docker image
build:
	docker build -t $(DOCKER_IMAGE) -f $(DOCKERFILE) .

# Run the Docker container
run:
	docker run --rm -it $(DOCKER_IMAGE)

# Clean up Docker images
clean:
	docker rmi $(DOCKER_IMAGE) || true

# Command to import SSL certificate (if needed)
import-cert:
	docker run --rm -v $(shell pwd):/app -w /app $(DOCKER_IMAGE) \
		keytool -importcert -file /tmp/$(CERT_FILE) \
		-alias $(ALIAS) -keystore $JAVA_HOME/lib/security/cacerts \
		-storepass changeit -noprompt

# Command to copy the certificate into the Docker image
copy-cert:
	docker cp $(CERT_FILE) $(DOCKER_IMAGE):/tmp/$(CERT_FILE)
