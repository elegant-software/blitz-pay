import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should return 409 when registering with an already-active registration number'

    request {
        method POST()
        url '/v1/merchants'
        headers {
            contentType(applicationJson())
        }
        body(
            merchantName: 'Duplicate GmbH',
            businessType: 'RETAIL',
            registrationNumber: 'DE-DUPLICATE',
            operatingCountry: 'DE'
        )
    }

    response {
        status 409
    }
}
