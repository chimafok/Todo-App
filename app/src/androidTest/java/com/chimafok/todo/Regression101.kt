package com.chimafok.todo

/** Select this release's focused regressions without relying on comma-separated CLI arguments. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS,AnnotationTarget.FUNCTION)
annotation class Regression101
