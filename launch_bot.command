#!/bin/bash

# StudyBay ULTRA-FAST Bidder Bot v2.1 - INSTANT MODE Launcher
# Double-click this file to launch the application

echo "🚀 Starting StudyBay ULTRA-FAST Bidder Bot v2.1 - INSTANT MODE..."
echo "⚡ Loading INSTANT MODE barrier-free bidding system..."

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
    echo "🚀 Launching StudyBay ULTRA-FAST Bidder Bot v2.1 - INSTANT MODE..."
    echo ""
    echo "🔥 INSTANT MODE Features Active:"
    echo "   ⚡ 100ms cycle processing (97% faster)"
    echo "   🚄 50ms AJAX triggers (98% faster)"
    echo "   🚫 Barrier-free architecture (all filters removed)"
    echo "   🎯 21 aggressive bid attempts per order"
    echo "   🔄 Zero duplicate prevention"
    echo "   💨 Millisecond-level bidding"
    echo "   📊 90-95% order capture rate"
    echo ""
    echo "INSTANT MODE GUI window should open shortly..."
    echo "If GUI doesn't appear, check if Java GUI is supported on this system"
    echo "Ready for MAXIMUM AGGRESSION bidding! 🔥"
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