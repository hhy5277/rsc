/*
rules = "scala:rsc.rules.RscCompat"
 */
package com.twitter.util

class FutureCancelledException extends Exception("The future was cancelled with Future.cancel")
