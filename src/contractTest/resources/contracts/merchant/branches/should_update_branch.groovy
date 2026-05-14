import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should update branch details and return updated branch'

    request {
        method PUT()
        url '/v1/merchants/00000000-0000-0000-0000-000000000001/branches/00000000-0000-0000-0000-000000000010'
        headers {
            contentType(applicationJson())
        }
        body(
            name: 'Updated Branch',
            active: true,
            addressLine1: 'Main Street 1',
            city: 'Berlin',
            postalCode: '10115',
            country: 'DE',
            contactFullName: 'Store Manager',
            website: 'https://branch.example.com',
            contactEmail: 'branch@test.de',
            contactPhoneNumber: '+49305555555',
            activePaymentChannels: ['TRUELAYER'],
            latitude: 52.52,
            longitude: 13.405,
            geofenceRadiusMeters: 200
        )
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            id: '00000000-0000-0000-0000-000000000010',
            merchantId: '00000000-0000-0000-0000-000000000001',
            name: 'Updated Branch',
            active: true,
            contactFullName: 'Store Manager',
            website: 'https://branch.example.com',
            contactEmail: 'branch@test.de'
        )
    }
}
