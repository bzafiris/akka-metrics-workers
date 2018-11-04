Based on Akka tutorial available here [Akka cluster](http://doc.akka.io/docs/akka/2.5/scala/cluster-usage.html) features.

## A simple FrontEnd/BackEnd/Worker example

To run the example in different JVMs do the following:

In the first terminal window, start the first seed node with the following command:

    sbt "runMain gr.aueb.metrics.cluster.MetricsFrontEnd projectName path 2551"

2551 corresponds to the port of the first seed-nodes element in the configuration. In the log output you see that the cluster node has been started and changed status to 'Up'.

In the second terminal window, start the second seed node with the following command:

        sbt "runMain gr.aueb.metrics.cluster.MetricsBackend 2552"

2552 corresponds to the port of the second seed-nodes element in the configuration. In the log output you see that the cluster node has been started and joins the other seed node and becomes a member of the cluster. Its status changed to 'Up'.

Switch over to the first terminal window and see in the log output that the member joined.

Start another node in the third terminal window with the following command:

    sbt "runMain gr.aueb.metrics.cluster.MetricsBackend 0"

Now you don't need to specify the port number, 0 means that it will use a random available port. It joins one of the configured seed nodes. Look at the log output in the different terminal windows.

Start even more nodes in the same way, if you like.

Shut down one of the nodes by pressing 'ctrl-c' in one of the terminal windows. The other nodes will detect the failure after a while, which you can see in the log output in the other terminals.

Look at the source code of the actor again. It registers itself as subscriber of certain cluster events. It gets notified with an snapshot event, `CurrentClusterState` that holds full state information of the cluster. After that it receives events for changes that happen in the cluster.


