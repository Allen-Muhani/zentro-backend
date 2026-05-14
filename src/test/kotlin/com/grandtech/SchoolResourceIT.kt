package com.grandtech

import io.quarkus.test.junit.QuarkusIntegrationTest

/**
 * Integration tests for [SchoolResource] that run against a packaged Quarkus application.
 *
 * Inherits all test cases from [SchoolResourceTest] and re-executes them against the
 * fully assembled application to verify the end-to-end HTTP layer.
 */
@QuarkusIntegrationTest
class SchoolResourceIT : SchoolResourceTest()