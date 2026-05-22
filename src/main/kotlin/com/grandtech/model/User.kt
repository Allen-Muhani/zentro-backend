package com.grandtech.model

/** Base class for every user account in the Zentro system. */
abstract class User {

    /** Firebase Authentication UID — unique identifier shared with the auth provider. */
    abstract val fedUid: String?
}