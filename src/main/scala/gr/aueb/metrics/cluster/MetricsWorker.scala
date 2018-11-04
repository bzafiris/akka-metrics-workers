package gr.aueb.metrics.cluster

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberUp, UnreachableMember}

import scala.concurrent.duration._

/**
  * Checkouts a git repository and
  * collects and converts metrics from SourceMeter execution
  * on specific revisions
  *
  * Executes the command
  * gradle run --args='collect ../work-1/revisions.txt ../SourceMeter-8.2.0-x64-linux/Java/SourceMeterJava ../jfreechart-gh/src ../jfreechart-gh ../work-1 jfreechart'
  *
  * convert results to csv
  * gradle run --args='convert ../work-1/jfreechart ../work-1/f19cea1ef6daccda17b9999264481c5b517861d8.csv'
  *
  */
class MetricsWorker(id: String, repoUrl: String, projectName: String, executablePath: String) extends Actor with ActorLogging {

  import MetricsWorker._

  val cluster = Cluster(context.system)
  /*

  1. Make dir ../work-$id
  2. Clone project inside ../work-$id as "projectName"
  3. Invoke source meter for each received revision with ouput directory "project-out"

   */

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
    log.info("Worker {} started", id)
    // initialize directories here
  }


  override def receive: Receive = {

    case MemberUp(member) =>
      log.info("Member is Up: {}", member.address)

    case MetricsJob(revision) =>
      log.info("Processing revision {}", revision)
      import scala.concurrent.ExecutionContext.Implicits.global
      val metricsBackend = sender()
      context.system.scheduler.scheduleOnce(5.seconds) { // send after 5 seconds
        //log.info("Job finished. Returning result ")
        metricsBackend ! MetricsResult(revision)
      }

  }

}

object MetricsWorker {

  def props(id: String, repoUrl: String, projectName: String, executablePath: String): Props
  = Props(new MetricsWorker(id, repoUrl, projectName, executablePath))

  final case class MetricsJob(revision: String)
  final case class MetricsResult(revision: String)

}
