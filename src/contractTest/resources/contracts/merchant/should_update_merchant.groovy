import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should update editable merchant fields and return updated details'

    request {
        method PUT()
        url '/v1/merchants/00000000-0000-0000-0000-000000000002'
        headers {
            contentType(applicationJson())
        }
        body(
            merchantName: 'After GmbH',
            merchantStatus: 'INACTIVE',
            contactInfo: [
                email: 'after@test.de',
                phoneNumber: '+49309999999'
            ],
            address: [
                addressLine1: 'New Strasse 9',
                city: 'Berlin',
                postalCode: '10115',
                country: 'DE'
            ],
            activePaymentChannels: ['STRIPE', 'TRUELAYER']
        )
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            applicationId: '00000000-0000-0000-0000-000000000002',
            merchantName: 'After GmbH',
            merchantStatus: 'INACTIVE',
            contactInfo: [
                email: 'after@test.de'
            ],
            address: [
                addressLine1: 'New Strasse 9'
            ]
        )
    }
}
