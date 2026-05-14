import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should return 404 when merchant id is not found'

    request {
        method GET()
        url '/v1/merchants/00000000-0000-0000-0000-000000000099'
    }

    response {
        status NOT_FOUND()
    }
}
