package tech.orkestra.utils

import java.time.Instant

import cats.effect.{Async, IO, Timer}
import cats.implicits._

import scala.concurrent.Future
import scala.concurrent.duration._
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.{ElasticClient, JavaClientExceptionWrapper}
import com.sksamuel.elastic4s.indexes.IndexRequest
import io.circe.Encoder
import shapeless._
import tech.orkestra.OrkestraConfig
import tech.orkestra.model.Indexed._
import tech.orkestra.model.RunInfo
import tech.orkestra.utils.AkkaImplicits._

object Elasticsearch {
  def client(implicit orkestraConfig: OrkestraConfig) = ElasticClient(orkestraConfig.elasticsearchProperties)

  def init[F[_]: Async](implicit elasticsearchClient: ElasticClient, timer: Timer[F]): F[Unit] =
    IO.fromFuture(IO {
        Future
          .traverse(indices)(indexDef => elasticsearchClient.execute(indexDef.createIndexRequest))
          .map(_ => ())
      })
      .to[F]
      .recoverWith {
        case JavaClientExceptionWrapper(_) =>
          timer.sleep(1.second) *>
            init
      }

  def indexRun[Parameters <: HList: Encoder](
    runInfo: RunInfo,
    parameters: Parameters,
    tags: Seq[String],
    parent: Option[RunInfo]
  ): IndexRequest = {
    val now = Instant.now()
    indexInto(HistoryIndex.index, HistoryIndex.`type`)
      .id(HistoryIndex.formatId(runInfo))
      .source(Run[Parameters, Unit](runInfo, parameters, now, parent, now, None, tags))
      .createOnly(true)
  }
}
