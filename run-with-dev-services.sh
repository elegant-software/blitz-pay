#!/bin/bash
# Script to run the BlitzPay application with Arconia-style dev services
# This will automatically start a PostgreSQL container and configure the application

echo "Starting BlitzPay with Arconia Dev Services..."
echo "This will automatically:"
echo "  - Start a PostgreSQL 16.2 container"
echo "  - Configure the DataSource"
echo "  - Run the application"
echo ""

# Set required environment variables (use dummy values for demonstration)
export TRUELAYER_CLIENT_ID=${TRUELAYER_CLIENT_ID:-dummy-client-id}
export TRUELAYER_CLIENT_SECRET=${TRUELAYER_CLIENT_SECRET:-dummy-secret}
export TRUELAYER_KEY_ID=${TRUELAYER_KEY_ID:-dummy-key-id}
export TRUELAYER_PRIVATE_KEY_PATH=${TRUELAYER_PRIVATE_KEY_PATH:-truelayer_pub.pem}
export TRUELAYER_MERCHANT_ACCOUNT_ID=${TRUELAYER_MERCHANT_ACCOUNT_ID:-dummy-merchant-id}

# Run the test application with dev services
./gradlew bootRun -PmainClass=com.elegant.software.quickpay.TestApplicationKt
