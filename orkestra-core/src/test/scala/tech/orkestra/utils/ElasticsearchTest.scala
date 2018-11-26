package tech.orkestra.utils

import cats.effect.{IO, Timer}

import scala.concurrent.duration._
import com.sksamuel.elastic4s.Indexes
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import org.http4s.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import tech.orkestra.utils.AkkaImplicits._

trait ElasticsearchTest extends BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures { self: Suite =>
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = 10.seconds)
  implicit def timer: Timer[IO]

  implicit val elasticsearchClient: ElasticClient = {
    val dockerHost = sys.env.get("DOCKER_HOST").flatMap(Uri.unsafeFromString(_).host).getOrElse("localhost")
    ElasticClient(ElasticProperties(s"http://$dockerHost:9200"))
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    (for {
      _ <- IO.fromFuture(IO(elasticsearchClient.execute(deleteIndex(Indexes.All.values))))
      _ <- Elasticsearch.init[IO]
    } yield ()).unsafeRunSync()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    elasticsearchClient.close()
  }
}
