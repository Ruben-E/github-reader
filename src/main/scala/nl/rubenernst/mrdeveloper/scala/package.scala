package nl.rubenernst.mrdeveloper

import scalaz._
import Scalaz._

package object scala {
  type DomainValidation[+A] = Validation[NonEmptyList[String], A]

  trait ValidationKey {
    def failNel = this.toString.failureNel
    def nel = NonEmptyList(this.toString)
    def failure = this.toString.failure
  }

  object CommonValidations {
    def checkString(s: String, err: ValidationKey): Validation[String, String] =
      if (s == null || s.isEmpty) err.failure else s.success

    def checkId(d: Long, err: ValidationKey): Validation[String, Long] =
      if (d < 0) err.failure else d.success

    def checkDate(d: Long, err: ValidationKey): Validation[String, Long] =
      if (d <= 0) err.failure else d.success
  }
}