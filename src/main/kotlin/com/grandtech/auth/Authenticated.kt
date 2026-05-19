package com.grandtech.auth

import jakarta.ws.rs.NameBinding

/**
 * Marks an endpoint or resource class as requiring a valid authenticated user.
 *
 * Applying this annotation causes [AuthFilter] to run before the handler, which:
 * 1. Verifies the `Authorization: Bearer <token>` header against Firebase.
 * 2. Confirms the resolved Firebase UID has a registered `User` node in Neo4j.
 *
 * If either check fails the request is aborted with HTTP 401 before the handler runs.
 * The verified Firebase UID is stored as a request property under the key `"fedUid"`
 * so handlers can access it via [jakarta.ws.rs.container.ContainerRequestContext].
 */
@NameBinding
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Authenticated