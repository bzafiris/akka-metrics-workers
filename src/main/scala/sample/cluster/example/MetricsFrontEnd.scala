package sample.cluster.example

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import sample.cluster.example.MetricsBackend
import sample.cluster.example.MetricsBackend.BackendRegistration
import sample.cluster.transformation.{TransformationFrontend, TransformationJob}

class MetricsFrontEnd extends Actor with ActorLogging {

  var backends = IndexedSeq.empty[ActorRef]
  var jobCounter = 0

  override def receive: Receive = {

    case BackendRegistration if !backends.contains(sender()) =>
      context watch sender()
      backends = backends :+ sender()
      log.info("Registered backend {}", sender().path.name)

    case Terminated(a) =>
      backends = backends.filterNot(_ == a)
      log.info("Unregistering backend {}", sender().path.name)

  }
}

object MetricsFrontEnd {

  def props(): Props = Props(new MetricsFrontEnd())

  def main(args: Array[String]): Unit = {
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(
      s"""
        akka.remote.netty.tcp.port=$port
        akka.remote.artery.canonical.port=$port
        """)
      .withFallback(ConfigFactory.parseString("akka.cluster.roles = [frontend]"))
      .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    val frontend = system.actorOf(MetricsFrontEnd.props(), name = "frontend")

  }

}