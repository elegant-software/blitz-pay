import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should create a new branch under a merchant and return 201'

    request {
        method POST()
        url '/v1/merchants/00000000-0000-0000-0000-000000000001/branches'
        headers {
            contentType(applicationJson())
        }
        body(
            name: 'Main Branch',
            addressLine1: 'Hauptstrasse 1',
            city: 'Berlin',
            postalCode: '10115',
            country: 'DE',
            contactFullName: 'Branch Manager',
            contactEmail: 'branch@acme.de',
            contactPhoneNumber: '+49301234567',
            activePaymentChannels: ['TRUELAYER'],
            latitude: 52.52,
            longitude: 13.405,
            geofenceRadiusMeters: 100
        )
    }

    response {
        status CREATED()
        headers {
            contentType(applicationJson())
        }
        body(
            id: $(consumer('00000000-0000-0000-0000-000000000010'), producer(regex('[0-9a-fA-F\\-]{36}'))),
            merchantId: '00000000-0000-0000-0000-000000000001',
            name: 'Main Branch',
            active: true,
            city: 'Berlin',
            country: 'DE',
            contactEmail: 'branch@acme.de',
            status: $(consumer('ACTIVE'), producer(regex('ACTIVE|INACTIVE')))
        )
    }
}
