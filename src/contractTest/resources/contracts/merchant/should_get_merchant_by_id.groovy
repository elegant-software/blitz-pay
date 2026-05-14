import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should return merchant details for a known merchant id'

    request {
        method GET()
        url '/v1/merchants/00000000-0000-0000-0000-000000000001'
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            applicationId: '00000000-0000-0000-0000-000000000001',
            merchantName: $(consumer('Test GmbH'), producer(regex('.+'))),
            registrationNumber: $(consumer('DE-CONTRACT-001'), producer(regex('.+'))),
            status: $(consumer('ACTIVE'), producer(regex('ACTIVE|PENDING|SUSPENDED|REJECTED'))),
            contactInfo: [
                email: $(consumer('test@test.de'), producer(regex('.+@.+')))
            ]
        )
    }
}
