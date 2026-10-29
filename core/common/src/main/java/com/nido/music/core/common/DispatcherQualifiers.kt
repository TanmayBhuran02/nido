package com.nido.music.core.common

import javax.inject.Qualifier

/**
 * Dispatcher qualifiers for constructor injection.
 * Avoids hard-coding Dispatchers.IO / Main / Default in production code.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
