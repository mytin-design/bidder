#!/bin/bash

# StudyBay Intelligent Bidder Bot v2.0 - Ultra-Fast Mode Launcher
# Double-click this file to launch the application

echo "🚀 Starting StudyBay Intelligent Bidder Bot v2.0 - Ultra-Fast Mode..."
echo "⚡ Loading ultra-fast intelligent bidding system..."

# Change to the application directory
cd "$(dirname "$0")"

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "❌ Error: Maven is not installed or not in PATH"
    echo "Please install Maven or run from terminal with full path"
    echo "Press any key to exit..."
    read -n 1
    exit 1
fi

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed or not in PATH"
    echo "Please install Java 11 or higher"
    echo "Press any key to exit..."
    read -n 1
    exit 1
fi

echo "✅ Dependencies check passed"
echo "🔧 Compiling application..."

# Compile the application
mvn compile > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
    echo "🚀 Launching StudyBay Intelligent Bidder Bot..."
    echo ""
    echo "🧠 Features Active:"
    echo "   ⚡ Ultra-fast processing (1-3 sec per order)"
    echo "   🎯 Context-aware bid messages"
    echo "   🔍 Intelligent order analysis"
    echo "   🚄 Search page direct processing"
    echo "   📊 Real-time performance monitoring"
    echo ""
    echo "GUI window should open shortly..."
    echo "If GUI doesn't appear, check if Java GUI is supported on this system"
    echo ""
    
    # Launch the application
    mvn exec:java -Dexec.mainClass="bot.BidderApp"
else
    echo "❌ Compilation failed"
    echo "Please check the code for errors"
    echo "Press any key to exit..."
    read -n 1
    exit 1
fi