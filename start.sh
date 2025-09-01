#!/bin/bash

echo "Starting StudyBay Bidder Bot..."

# Build the application
echo "Building application..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo "Build failed. Please check the error messages above."
    exit 1
fi

echo "Build successful! Starting application..."

# Run the application
mvn exec:java -Dexec.mainClass="bot.BidderApp"
