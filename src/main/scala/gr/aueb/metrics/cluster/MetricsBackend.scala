package gr.aueb.metrics.cluster

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, RootActorPath}
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.{Cluster, Member}
import com.typesafe.config.ConfigFactory
import gr.aueb.metrics.cluster.MetricsWorker.{MetricsJob, MetricsResult}

class MetricsBackend extends Actor with ActorLogging {

  import MetricsBackend._;
  var requestedJobs = IndexedSeq.empty[String];
  var pendingJobs = IndexedSeq.empty[String];
  var workers = IndexedSeq.empty[ActorRef]
  //var revisionToFrontEnd = Map.empty[String, ActorRef]
  //var revisionToJobId = Map.empty[String, String]
  /**
    * Serves one job from a single frontend
    */
  var frontEnd = Option[ActorRef](null)
  var batchId = Option[String](null)

  val cluster = Cluster(context.system)

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = {

    case MetricsBatchJob(id, revisions) =>
      log.info("****** Received batch with id {}. Processing ...", id)
      requestedJobs = revisions.toIndexedSeq
      if (workers.isEmpty){
        // start workers
        Stream.range(0, 5).foreach(x => {
          val workerActor = context.actorOf(
            MetricsWorker.props(x.toString,
              "repo", "project", "sourceMeterPath"),
            s"worker-${x}")
          workers = workers :+ workerActor
        })
      }
      frontEnd = Option(sender())
      batchId = Option(id)
      for(worker <- workers){
        assignWork(worker)
      }

    case MetricsResult(revision) =>
      log.info("{} <{}, {}>", revision, requestedJobs.size, pendingJobs.size)
      pendingJobs = pendingJobs.filterNot(_ == revision)

      if (pendingJobs.isEmpty && requestedJobs.isEmpty){
        if (frontEnd.isDefined && batchId.isDefined){
          frontEnd.get ! MetricsBatchResult(batchId.get)
          log.info("****** Finished batch {}", batchId.get)
        }
      } else {
        assignWork(sender())
      }

      // once any member becomes available, the current node is notified
    case MemberUp(m) => register(m)
      def register(member: Member): Unit =
        if (member.hasRole("frontend"))
          context.actorSelection(RootActorPath(member.address) / "user" / "frontend") !
            BackendRegistration
  }

  def assignWork(worker: ActorRef): Unit =
    if (!requestedJobs.isEmpty) {
      val nextRevision = requestedJobs.head
      requestedJobs = requestedJobs.filterNot(_ == nextRevision)
      pendingJobs = pendingJobs :+ nextRevision
      worker ! MetricsJob(nextRevision)
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


