package io.chumps.orchestra.utils

import java.io.IOException

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.sys.process

import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.goyeau.kubernetesclient.KubernetesException
import io.k8s.api.core.v1.Container
import io.k8s.apimachinery.pkg.apis.meta.v1.Status

import AkkaImplicits._
import io.chumps.orchestra.OrchestraConfig
import io.chumps.orchestra.filesystem.Directory
import io.chumps.orchestra.kubernetes.Kubernetes

trait ShellHelpers {
  private def runningMessage(script: String) = println(s"Running: $script")

  def sh(script: String)(implicit workDir: Directory): String = {
    runningMessage(script)
    process.Process(Seq("sh", "-c", script), workDir.file).lineStream.fold("") { (acc, line) =>
      println(line)
      s"$acc\n$line"
    }
  }

  def sh(script: String, container: Container)(implicit workDir: Directory): String = {
    runningMessage(script)
    val stageId = StagesHelpers.stageVar.value

    val sink = Sink.fold[String, Either[Status, String]]("") { (acc, data) =>
      StagesHelpers.stageVar.withValue(stageId) {
        data match {
          case Left(Status(_, _, _, _, _, _, _, Some("Success"))) =>
            println()
            acc
          case Left(Status(_, _, _, _, Some(message), _, Some(reason), _)) =>
            throw new IOException(s"$reason: $message; Container: ${container.name}; Script: $script")
          case Left(status) =>
            throw new IOException(
              s"Non success container termination: $status; Container: ${container.name}; Script: $script"
            )
          case Right(log) =>
            print(log)
            acc + log
        }
      }
    }
    val flow = Flow.fromSinkAndSourceMat(sink, Source.maybe[Message])(Keep.left)

    def exec(timeout: Duration = 1.minute, interval: Duration = 300.millis): Future[String] =
      Kubernetes.client.pods
        .namespace(OrchestraConfig.namespace)(OrchestraConfig.podName)
        .exec(
          flow,
          Option(container.name),
          Seq("sh", "-c", s"cd ${workDir.file.getAbsolutePath} && $script"),
          stdin = true,
          tty = true
        )
        .recoverWith {
          case _: KubernetesException if timeout > 0.milli =>
            Thread.sleep(interval.toMillis)
            exec(timeout - interval, interval)
        }

    Await.result(exec(), Duration.Inf)
  }
}
