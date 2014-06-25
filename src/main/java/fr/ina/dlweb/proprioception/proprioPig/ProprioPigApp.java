package fr.ina.dlweb.proprioception.proprioPig;

import fr.ina.dlweb.hadoop.HadoopClient;
import fr.ina.dlweb.hadoop.HadoopClientCDH4;
import fr.ina.dlweb.hadoop.HadoopClientClassic;
import fr.ina.dlweb.pig.PigClient;
import fr.ina.dlweb.proprioception.models.ChartInfo;
import fr.ina.dlweb.proprioception.models.PigJob;
import fr.ina.dlweb.proprioception.models.PigScript;
import fr.ina.dlweb.proprioception.models.Result;
import fr.ina.dlweb.utils.Properties2;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Collection;

/**
 * Date: 13/11/12
 * Time: 16:35
 *
 * @author drapin
 */
@SuppressWarnings({"UnusedDeclaration"})
public class ProprioPigApp
{
    private static final Logger log = Logger.getLogger(ProprioPigApp.class);

    private final PigClient pigClient;
    private final String storePath;
    private PersistenceStore store;
    private PigJob currentJob = null;

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    public ProprioPigApp(boolean cdh4, String configName, boolean loadStore)
    {
        HadoopClient hc;
        Properties2 properties;
        properties = new Properties2(ProprioPigApp.class, configName + ".properties");
        String cacheDir = "./hadoop-config-" + configName;

        // old cluster (Hadoop 0.20)
        if (!cdh4) {
            hc = new HadoopClientClassic(
                properties.getString("hadoop.server.host"),
                properties.getInteger("hadoop.server.ssh.port", null),
                properties.getString("hadoop.server.ssh.user"),
                properties.getString("hadoop.server.ssh.password"),
                properties.getString("hadoop.path"),
                cacheDir
            );
            hc.fetchConfig();
            pigClient = new PigClient(hc, properties.getString("hadoop.pig.tmp.folder"));
        }

        // new cluster (CHD4)
        else {
            hc = new HadoopClientCDH4(
                properties.getString("hadoop.clouderaManager.host"),
                properties.getInteger("hadoop.clouderaManager.port"),
                properties.getInteger("hadoop.clouderaManager.mapReduceServiceId"),
                properties.getString("hadoop.userName"),
                cacheDir
            );
            hc.fetchConfig();
            pigClient = new PigClient(hc, "/user/" + properties.getString("hadoop.userName"));
        }

        storePath = properties.getString("proprioception.store");
        if (loadStore) {
            try {
                store = PersistenceStore.load(storePath);
            } catch (Exception e) {
                log.error(null, e);
                store = new PersistenceStore();
            }
        } else {
            store = new PersistenceStore();
        }
    }

//    public static void patchPackageImportList()
//    {
//        if (PigContext.getPackageImportList().size() < 4) {
//            // add package paths for extensions
//            ArrayList<String> packages = new ArrayList<String>();
//            packages.addAll(PigContext.getPackageImportList());
//            packages.add(DAFFLoader.class.getPackage().getName() + ".");
//            packages.add(ExtractValue.class.getPackage().getName() + ".");
//            PigContext.setPackageImportList(packages);
//        }
//    }

    /**
     * launches a job for an existing pig script
     *
     * @param scriptName   the name of the existing script
     * @param deleteResult whether to delete the result directory after reading it on not.
     */
    public synchronized void startPigJob(String scriptName, boolean deleteResult)
    {
        PigScript script = store.getScript(scriptName);
        if (script == null) {
            log.warn("no script found for name '" + scriptName + "'");
            return;
        }
        store.enqueue(new PigJob(store.getNextResultId(), script, deleteResult));
        consume();
    }

    /**
     * creates a new script (not persisted) and launches a job for it
     *
     * @param scriptName the name of the new script
     * @param query      the query string
     */
    public synchronized void startPigJob(String scriptName, String query, ChartInfo chartInfo)
    {
        PigScript script = new PigScript(scriptName, query, pigClient, chartInfo);
        store.enqueue(new PigJob(store.getNextResultId(), script, true));
        consume();
    }

    public synchronized PigScript addPigScript(String scriptName, String query, ChartInfo chartInfo)
    {
        PigScript pigScript = new PigScript(scriptName, query, pigClient, chartInfo);
        store.addScript(pigScript);
        return pigScript;
    }

    public synchronized PigScript updatePigScript(
        String scriptName, String newQuery, String newName, ChartInfo chartInfo
    )
    {
        PigScript script = store.getScript(scriptName);
        return store.updateScript(script, new PigScript(newName, newQuery, pigClient, chartInfo));
    }

    private synchronized void consume()
    {
        if (currentJob != null) return;

        currentJob = store.dequeue();
        if (currentJob == null) return;

        JobResultListener listener = new JobResultListener(currentJob, currentJob.isDeleteResult())
        {
            @Override
            protected void onDone(PigJob job)
            {
                currentJob = null;
                onJobDone(job);
                consume();
            }
        };

        currentJob.runJob(listener, pigClient, true);
    }

    public synchronized void onJobDone(PigJob job)
    {
        if (job.getFailure() != null) {
            store.addDone(job.getFailure());
        } else if (job.getSuccess() != null) {
            store.addDone(job.getSuccess());
        }
    }

    public PigJob getCurrentJob()
    {
        return currentJob;
    }

    public Collection<Result> getResults()
    {
        return store.getResults();
    }

    public int getJobQueueSize()
    {
        return store.getJobQueueSize();
    }

    public Collection<PigScript> getScripts()
    {
        return store.getScripts();
    }

    public Result getResult(Integer jobId)
    {
        return store.getResult(jobId);
    }

    public PigScript getPigScript(String scriptName)
    {
        return store.getScript(scriptName);
    }

    public void saveStore()
    {
        store.save(storePath);
    }

    public void deleteScript(String scriptName)
    {
        store.deleteScript(scriptName);
    }

    public void deleteResult(int jobId)
    {
        store.deleteResult(jobId);
    }

    public void setDefaultReduceParallelism(int defaultReduceParallelism)
    {
        pigClient.setDefaultParallel(defaultReduceParallelism);
    }

    public void updateResult(int jobId, ChartInfo chartInfo)
    {
        store.updateResult(jobId, chartInfo);
    }

    public PigClient getClient()
    {
        return pigClient;
    }

    public String getFooter()
    {
        String s = "";

        boolean jdk = false;
        try {
            Class.forName("com.sun.tools.javac.Launcher");
            jdk = true;
        } catch (ClassNotFoundException e) { /* ignore */ }

        s += (jdk ? "JDK" : "JRE") + " - ";
        s += "java.version='" + System.getProperty("java.version") + "' - ";
        s += "store='" + new File(storePath).getAbsolutePath() + "'";
        return s;
    }

//    public void testSerializeContext() throws IOException, ClassNotFoundException, NoSuchFieldException,
//        IllegalAccessException
//    {
//        PigContext c = pigClient.getPigServer().getPigContext();
//        c.addJar(new URL(ClassUtils.getJarPath(DAFFLoader.class)));
//        System.out.println(">GO");
//        if (c.extraJars != null) for (URL j : c.extraJars) System.out.println(">ej1:" + j);
//        ByteArrayOutputStream bao = new ByteArrayOutputStream(1024*1024);
//        ObjectOutputStream oas = new ObjectOutputStream(bao);
//        oas.writeObject(c);
//        oas.flush();
//        oas.close();
//        byte[] data = bao.toByteArray();
//        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
//        PigContext c2 = (PigContext) ois.readObject();
//
//        Field clf = PigContext.class.getDeclaredField("classloader");
//        clf.setAccessible(true);
//        Object loader = clf.get(c2);
//        Class clazz = Class.forName("fr.ina.dlweb.pig.io.DAFFLoader", true, (ClassLoader) loader);
//        System.out.println(">" + clazz);
//
//        if (c2.extraJars != null) for (URL j : c2.extraJars) System.out.println(">ej2:" + j);
//        System.out.println(">DONE");
//    }
}
