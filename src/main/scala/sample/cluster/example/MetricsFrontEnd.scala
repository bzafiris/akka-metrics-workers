package sample.cluster.example

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import sample.cluster.example.MetricsBackend
import sample.cluster.example.MetricsBackend.{BackendRegistration, MetricsBatchResult}
import sample.cluster.example.MetricsFrontEnd.Job
import sample.cluster.transformation.{TransformationFrontend, TransformationJob}

class MetricsFrontEnd(repoPath: String, projectName: String) extends Actor with ActorLogging {

  var backends = IndexedSeq.empty[ActorRef]
//  var jobCounter = 0
//  var jobIdToActor = Map.empty[String, ActorRef]
  var pendingJobs = IndexedSeq.empty[Job]


  override def preStart(): Unit = {
    log.info("Starting frontend ...")

    Stream.range(0, 1000, 1)
      .map(x => x.toString).grouped(10)
      .zipWithIndex
      .map(x => Job(x._2.toString, x._1.toArray))
      .foreach(x => {
        //println(s"${x.id} - ${x.revisions.length}")
        pendingJobs = pendingJobs :+ x;
      })

    log.info(s"Jobs pending ${pendingJobs.size}")
  }



  override def receive: Receive = {

    case BackendRegistration if !backends.contains(sender()) =>
      context watch sender()
      backends = backends :+ sender()
      log.info("Registered backend {}", sender().path.name)
      if (!pendingJobs.isEmpty){
        val nextJob = pendingJobs.head
        pendingJobs = pendingJobs.tail
        sender() ! MetricsBackend.MetricsBatchJob(nextJob.id, nextJob.revisions)
      }

    case Terminated(a) =>
      backends = backends.filterNot(_ == a)
      log.info("Unregistering backend {}", sender().path.name)

    case MetricsBatchResult(jobId) =>
      log.info("Job with id {} finished", jobId)
      pendingJobs = pendingJobs.filterNot(_.id == jobId)
      log.info(s"Jobs pending ${pendingJobs.size}")
      if (!pendingJobs.isEmpty){
        val nextJob = pendingJobs.head
        pendingJobs = pendingJobs.tail
        sender() ! MetricsBackend.MetricsBatchJob(nextJob.id, nextJob.revisions)
      }

  }
}

object MetricsFrontEnd {


  final case class Job(id: String, revisions: Array[String])

  def props(repoPath: String, projectName: String): Props = Props(new MetricsFrontEnd(repoPath, projectName))

  def main(args: Array[String]): Unit = {

    /*
    args[0] -> repoPath
    args[1] -> port number
     */

    if (args.length < 2){
      println("Usage: MetricsFrontEnd projectName repoPath [port]")
      return;
    }

    val port = if (args.length < 3) "0" else args(2)
    val repoPath = args(1)
    val projectName = args(0)

    val config = ConfigFactory.parseString(
      s"""
        akka.remote.netty.tcp.port=$port
        akka.remote.artery.canonical.port=$port
        """)
      .withFallback(ConfigFactory.parseString("akka.cluster.roles = [frontend]"))
      .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    val frontend = system.actorOf(MetricsFrontEnd.props(repoPath, projectName), name = "frontend")

  }

}