package fr.ina.dlweb.proprioception.web;

import fr.ina.dlweb.proprioception.csvChart.CsvChartServlet;
import fr.ina.dlweb.proprioception.facetRedux.servlet.FacetServlet;
import fr.ina.dlweb.proprioception.proprioPig.servlet.ProprioPigServlet;
import fr.ina.dlweb.utils.ArgsParser;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;

import javax.servlet.http.HttpServlet;

/**
 * Date: 12/11/12
 * Time: 19:01
 *
 * @author drapin
 */
public class ProprioceptionServer
{
    private static final Logger log = Logger.getLogger(ProprioceptionServer.class);
    public static final int DEFAULT_PORT = 8080;
    public static final int DEFAULT_REDUCE_PARALLELISM = 7;
    public static final boolean DEFAULT_CLUSTER_OLD = false;

    public static void main(String[] args) throws Exception
    {
        //BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);

        ArgsParser params = ArgsParser.parseArgs(args);

        int port = params.get("port", DEFAULT_PORT);
        boolean oldCluster = params.get("old-cluster", DEFAULT_CLUSTER_OLD);
        int pigReduceParallelism = params.get("pig-parallelism", DEFAULT_REDUCE_PARALLELISM);

        params.usageInfo();
//        log.info("starting server (port='" + port
//            + "', old= '" + oldCluster
//            + "', parallelism=" + pigReduceParallelism
//            + ")"
//        );

        Server s = new Server(port);
        ServletHandler h = new ServletHandler();

        // Facet-Redux
        addServlet(h, "/facet/*", new FacetServlet(
            "dlwr00n02.ina.fr"
        ));

        // CSV-Charts
        addServlet(h, "/chart/*", new CsvChartServlet());

        // Proprio-Pig
        addServlet(h, "/propriopig/*", new ProprioPigServlet(
            pigReduceParallelism,
            oldCluster
        ));

        s.addHandler(h);
        s.start();
    }

    private static void addServlet(ServletHandler h, String path, HttpServlet servlet)
    {
        ServletHolder sh = new ServletHolder(servlet);
        h.addServletWithMapping(sh, path);
    }
}
