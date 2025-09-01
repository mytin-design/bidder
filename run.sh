#!/bin/bash

echo "Starting StudyBay Bidder Bot..."

# Check if JAR exists
if [ ! -f "target/bidder-bot-1.0.0.jar" ]; then
    echo "JAR file not found. Building application..."
    mvn clean package
fi

# Run the pre-built JAR directly
java -jar target/bidder-bot-1.0.0.jar
