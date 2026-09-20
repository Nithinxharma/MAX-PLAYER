package com.lagradost.cloudstream3.utils.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

open class NonEmptySerializer<T>(private val delegate: KSerializer<T>) : KSerializer<T> {
    override val descriptor: SerialDescriptor get() = delegate.descriptor
    override fun deserialize(decoder: Decoder): T = delegate.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: T) = delegate.serialize(encoder, value)
}
