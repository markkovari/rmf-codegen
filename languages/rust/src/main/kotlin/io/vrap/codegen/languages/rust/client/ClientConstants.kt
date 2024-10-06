package io.vrap.codegen.languages.rust.client

import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.di.ClientPackageName
import io.vrap.rmf.codegen.di.SharedPackageName

class ClientConstants constructor(
    @SharedPackageName val sharedPackage: String,
    @ClientPackageName val clientPackage: String,
    @BasePackageName val basePackageName: String
) {

    val indexFile = "main"
}
