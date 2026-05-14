import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should return branch details for a known merchant and branch id'

    request {
        method GET()
        url '/v1/merchants/00000000-0000-0000-0000-000000000001/branches/00000000-0000-0000-0000-000000000010'
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            id: '00000000-0000-0000-0000-000000000010',
            merchantId: '00000000-0000-0000-0000-000000000001',
            name: $(consumer('Main Branch'), producer(regex('.+'))),
            active: $(consumer(true), producer(regex('true|false'))),
            status: $(consumer('ACTIVE'), producer(regex('ACTIVE|INACTIVE')))
        )
    }
}
