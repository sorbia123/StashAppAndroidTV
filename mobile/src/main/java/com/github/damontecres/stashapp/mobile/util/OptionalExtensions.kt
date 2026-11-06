package com.github.damontecres.stashapp.mobile.util

import com.apollographql.apollo.api.Optional

fun Optional.Companion.presentIfNotNullOrBlank(value: String?): Optional<String> =
    if (!value.isNullOrBlank()) Optional.present(value) else Optional.absent()
