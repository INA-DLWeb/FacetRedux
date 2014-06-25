package fr.ina.dlweb.proprioception.facetRedux;

import fr.ina.dlweb.hadoop.HadoopClientCDH4;
import fr.ina.dlweb.mapreduce.MapReduceClientCDH4;
import fr.ina.dlweb.utils.ClassUtils;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;

import java.io.File;

/**
 * Date: 21/01/14
 * Time: 15:23
 *
 * @author drapin
 */
public class JobLauncher
{
    private final String[] args;
    private final Class<? extends Tool> job;

    public <T extends Configured & Tool> JobLauncher(Class<? extends T> job, String... args)
    {
        this.args = args;
        this.job = job;
    }

    public static HadoopClientCDH4 getHadoopClient()
    {
        HadoopClientCDH4 c = new HadoopClientCDH4(
            "dlwr00n01.ina.fr",
            7180,
            8,
            "public",
            "./hadoop-config-cache-00"
        );
        c.fetchConfig();
        return c;
    }

    public void run() throws Exception
    {
        // if the jar cannot be resolved, add manual path
        String autoPath = ClassUtils.getJarPath(job);
        System.out.println("AUTO_JAR_PATH: " + autoPath);

        String manualPath = null;
        if (autoPath == null || !autoPath.endsWith(".jar")) {
            manualPath = new File("./target/proprioception-web-0.2-SNAPSHOT-jar-with-dependencies.jar").getAbsolutePath();
            System.out.println("MANUAL_JAR_PATH: " + manualPath);
        }

        String[] manualJar = manualPath == null
            ? new String[0]
            : new String[]{manualPath};

        HadoopClientCDH4 client = getHadoopClient();
        client.fetchConfig();

        MapReduceClientCDH4 mrClient = new MapReduceClientCDH4(client);
        mrClient.runTool(
            job,
            args,
            manualJar
        );
    }
}
