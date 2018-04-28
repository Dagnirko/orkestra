package com.drivetribe.orchestra.cron

import com.drivetribe.orchestra.utils.DummyJobs._
import com.drivetribe.orchestra.utils.OrchestraConfigTest
import shapeless.test.illTyped

object CronTriggerStaticTests extends OrchestraConfigTest {

  object `Define a CronTrigger with job that has no parameters` {
    CronTrigger("*/5 * * * *", emptyJob)
  }

  object `Define a CronTrigger with job that has a parameter should not compile` {
    illTyped(
      """
      CronTrigger("*/5 * * * *", oneParamJob)
      """,
      "type mismatch;.+"
    )
  }

  object `Define a CronTrigger with job that has a RunId parameter should not compile` {
    illTyped(
      """
      CronTrigger("*/5 * * * *", emptyWithRunIdJob)
      """,
      "type mismatch;.+"
    )
  }
}
