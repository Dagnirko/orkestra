package tech.orkestra

import cats.effect.{ConcurrentEffect, ContextShift, IO, Timer}
import cats.implicits._
import org.scalatest.Matchers._
import tech.orkestra.Dsl._
import tech.orkestra.utils.DummyJobs._
import tech.orkestra.utils._
import org.scalatest.concurrent.Eventually
import shapeless._
import tech.orkestra.job.Jobs

import scala.concurrent.ExecutionContext

class TriggersTests
    extends OrkestraSpec
    with OrkestraConfigTest
    with KubernetesTest[IO]
    with ElasticsearchTest
    with Triggers[IO]
    with Eventually {
  implicit lazy val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit lazy val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit lazy val F: ConcurrentEffect[IO] = IO.ioConcurrentEffect

  "Trigger a job" should "start a job given empty parameter" in usingKubernetesClient { implicit kubernetesClient =>
    emptyJob.trigger(HNil) *>
      IO(eventually {
        val runningJobs = CommonApiServer().runningJobs().futureValue
        (runningJobs should have).size(1)
      })
  }

  it should "start a job given 1 parameter" in usingKubernetesClient { implicit kubernetesClient =>
    oneParamJob.trigger("someString" :: HNil) *>
      IO(eventually {
        val runningJobs = CommonApiServer().runningJobs().futureValue
        (runningJobs should have).size(1)
      })
  }

  it should "start a job given multiple parameters" in usingKubernetesClient { implicit kubernetesClient =>
    twoParamsJob.trigger("someString" :: true :: HNil) *>
      IO(eventually {
        val runningJobs = CommonApiServer().runningJobs().futureValue
        (runningJobs should have).size(1)
      })
  }

  "Run a job" should "start a job and await result given empty parameter" in usingKubernetesClient {
    implicit kubernetesClient =>
      for {
        run <- emptyJob.run(HNil).start
        _ = eventually {
          val runningJobs = CommonApiServer().runningJobs().futureValue
          (runningJobs should have).size(1)
        }

        _ <- IO.fromFuture(IO(Jobs.succeedJob(orkestraConfig.runInfo, ())))
        _ <- kubernetes.Jobs.delete(orkestraConfig.runInfo)
        _ <- run.join
        _ = eventually {
          val runningJobs2 = CommonApiServer().runningJobs().futureValue
          (runningJobs2 should have).size(0)
        }
      } yield ()
  }

  it should "start a job and await result given 1 parameter" in usingKubernetesClient { implicit kubernetesClient =>
    for {
      run <- oneParamJob.run("someString" :: HNil).start
      _ = eventually {
        val runningJobs = CommonApiServer().runningJobs().futureValue
        (runningJobs should have).size(1)
      }

      _ <- IO.fromFuture(IO(Jobs.succeedJob(orkestraConfig.runInfo, ())))
      _ <- kubernetes.Jobs.delete(orkestraConfig.runInfo)
      _ <- run.join
      _ = eventually {
        val runningJobs2 = CommonApiServer().runningJobs().futureValue
        (runningJobs2 should have).size(0)
      }
    } yield ()
  }

  it should "start a job and await result given multiple parameters" in usingKubernetesClient {
    implicit kubernetesClient =>
      for {
        run <- twoParamsJob.run("someString" :: true :: HNil).start
        _ = eventually {
          val runningJobs = CommonApiServer().runningJobs().futureValue
          (runningJobs should have).size(1)
        }

        _ <- IO.fromFuture(IO(Jobs.succeedJob(orkestraConfig.runInfo, ())))
        _ <- kubernetes.Jobs.delete(orkestraConfig.runInfo)
        _ <- run.join
        _ = eventually {
          val runningJobs2 = CommonApiServer().runningJobs().futureValue
          (runningJobs2 should have).size(0)
        }
      } yield ()
  }
}
