import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should register a new merchant and return ACTIVE summary with 201'

    request {
        method POST()
        url '/v1/merchants'
        headers {
            contentType(applicationJson())
        }
        body(
            merchantName: 'Acme GmbH',
            businessType: 'RETAIL',
            registrationNumber: 'DE123456789',
            operatingCountry: 'DE'
        )
    }

    response {
        status CREATED()
        headers {
            contentType(applicationJson())
        }
        body(
            applicationId: $(consumer('00000000-0000-0000-0000-000000000001'), producer(regex('[0-9a-fA-F\\-]{36}'))),
            applicationReference: $(consumer('BLTZ-0001'), producer(regex('BLTZ-[A-Z0-9]+'))),
            merchantCode: $(consumer(''), producer(regex('.*'))),
            merchantName: 'Acme GmbH',
            merchantStatus: 'ACTIVE',
            registrationNumber: 'DE123456789',
            status: 'ACTIVE'
        )
    }
}