package sample.cluster.example

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, RootActorPath}
import akka.cluster.{Cluster, Member}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import com.typesafe.config.ConfigFactory

class MetricsBackend extends Actor with ActorLogging {

  import MetricsBackend._;

  val cluster = Cluster(context.system)

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = {

    case MetricsBatchJob(id, revisions) => log.info("Received batch with id {}", id)

      // once any member becomes available, the current node is notified
    case MemberUp(m) => register(m)

      def register(member: Member): Unit =
        if (member.hasRole("frontend"))
          context.actorSelection(RootActorPath(member.address) / "user" / "frontend") !
            BackendRegistration
  }

}

object MetricsBackend {
  def props(): Props = Props(new MetricsBackend())

  final case class MetricsBatchJob(jobId: String, revisions: Array[String])

  final case class MetricsBatchResult(jobId: String)

  case object BackendRegistration

  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(
      s"""
        akka.remote.netty.tcp.port=$port
        akka.remote.artery.canonical.port=$port
        """)
      .withFallback(ConfigFactory.parseString("akka.cluster.roles = [backend]"))
      .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(MetricsBackend.props(), name = "metricsBackend")

    //system.actorOf(Props[MetricsListener], name = "metricsListener")
  }
}


