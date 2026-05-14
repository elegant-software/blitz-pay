import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should store branch image storage key and return signed image url'

    request {
        method PUT()
        url '/v1/merchants/00000000-0000-0000-0000-000000000001/branches/00000000-0000-0000-0000-000000000010/image'
        headers {
            contentType(applicationJson())
        }
        body(
            storageKey: 'merchants/00000000-0000-0000-0000-000000000001/branches/00000000-0000-0000-0000-000000000010/image.webp'
        )
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            imageUrl: $(consumer('https://signed.example/merchants/00000000-0000-0000-0000-000000000001/branches/00000000-0000-0000-0000-000000000010/image.webp'), producer(regex('https://.+')))
        )
    }
}
