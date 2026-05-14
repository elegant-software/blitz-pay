import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should return list of branches for a merchant'

    request {
        method GET()
        url '/v1/merchants/00000000-0000-0000-0000-000000000001/branches'
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body($(consumer([]), producer(regex('\\[.*\\]'))))
    }
}
