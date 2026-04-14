import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should accept payment creation request and return generated paymentRequestId'

    request {
        method POST()
        url '/v1/payments/request'
        headers {
            contentType(applicationJson())
        }
        body(
            paymentRequestId: null,
            orderId: 'ORDER-123',
            amountMinorUnits: 1099,
            currency: 'EUR',
            userDisplayName: 'Jane Doe',
            redirectReturnUri: 'https://merchant.example.com/return'
        )
    }

    response {
        status ACCEPTED()
        headers {
            contentType(applicationJson())
        }
        body(
            paymentRequestId: $(consumer('123e4567-e89b-12d3-a456-426614174000'), producer(regex('[0-9a-fA-F\\-]{36}'))),
            paymentId: 'contract-test-payment-id',
            resourceToken: 'contract-test-resource-token',
            redirectReturnUri: 'https://merchant.example.com/return'
        )
    }
}
