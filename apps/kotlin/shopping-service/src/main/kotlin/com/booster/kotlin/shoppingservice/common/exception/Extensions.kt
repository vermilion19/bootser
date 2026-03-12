package com.booster.kotlin.shoppingservice.common.exception

import java.util.Optional

fun <T> Optional<T>.orThrow(lazyException: () -> Exception): T =
    orElseThrow { lazyException() }