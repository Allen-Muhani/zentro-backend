package com.grandtech.school

import io.quarkus.test.junit.QuarkusIntegrationTest

/**
 * Integration tests for school endpoints that run against the packaged Quarkus application.
 *
 * Inherits and re-executes the no-mock [GetSchoolsTest] cases against the fully assembled
 * application to verify end-to-end HTTP routing and JSON serialisation.
 */
@QuarkusIntegrationTest
class SchoolResourceIT : GetSchoolsTest()