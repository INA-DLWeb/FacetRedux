package fr.ina.dlweb.proprioception.models;

import fr.ina.dlweb.pig.PigClient;
import fr.ina.dlweb.pig.PigResultListener;
import org.apache.log4j.Logger;
import org.apache.pig.PigServer;
import org.apache.pig.data.Tuple;

import java.util.Arrays;
import java.util.Date;

/**
 * Date: 13/11/12
 * Time: 17:57
 *
 * @author drapin
 */
@SuppressWarnings({"UnusedDeclaration", "unchecked"})
public class PigJob extends PigTask
{
    private static final Logger log = Logger.getLogger(PigJob.class);
    private static final java.util.List<? extends java.lang.Class> DEPENDENCIES_CLASSES = Arrays.asList(
        // dlweb-commons
        fr.ina.dlweb.utils.DateUtil.class,
        // daff-io
        fr.ina.dlweb.daff.DAFFUtils.class,
        // hadoop-tools
        fr.ina.dlweb.pig.io.DAFFLoader.class,
        // ant
        org.apache.tools.bzip2.CBZip2OutputStream.class,
        // jvyaml
        org.jvyaml.DefaultYAMLFactory.class,
        // jackson-datebind
        com.fasterxml.jackson.databind.ObjectMapper.class,
        // jackson-core
        com.fasterxml.jackson.core.JsonFactory.class,
        // json annotations
        com.fasterxml.jackson.annotation.JsonAutoDetect.class
    );

    public final int id;
    private int progress = 0;
    public Success success = null;

    private final Date dateAdded;
    private Date dateStarted;

    private final String query;
    private final String name;
    private final ChartInfo chartInfo;
    private final PigServer parsedQuery;
    private boolean deleteResult = true;

    public PigJob(int jobId, PigScript script, boolean deleteResult)
    {
        this.id = jobId;
        this.parsedQuery = script.getParsedQuery();
        this.query = script.getQuery();
        this.name = script.getName();
        this.chartInfo = script.getChartInfo();
        this.deleteResult = deleteResult;
        dateAdded = new Date();
    }

    public void runJob(final PigResultListener listener, final PigClient client, boolean detached)
    {
        dateStarted = new Date();
        success = null;

        Runnable job = new Runnable()
        {
            @Override
            public void run()
            {
                PigServer parsed = parsedQuery;

                if (parsed == null) {
                    parsed = PigScript.parseQuery(query, client);
                }

                parsed.setJobName(name);
                parsed.getPigContext().getProperties().setProperty("job.name", name);

                // PATCH for CDH4 BUG
                String originalPathSeparator = System.getProperty("path.separator");
                System.setProperty("path.separator", ":");

                client.runPigScript(
                    listener,
                    DEPENDENCIES_CLASSES,
                    parsed
                );

                // PATCH for CDH4 BUG
                System.setProperty("path.separator", originalPathSeparator);
            }
        };

        if (detached) {
            Thread jobThread = new Thread(job, "pig_job_" + id);
            jobThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler()
            {
                @Override
                public void uncaughtException(Thread t, Throwable e)
                {
                    if (e instanceof Exception) {
                        listener.onError((Exception) e);
                    } else {
                        log.error(null, e);
                    }
                }
            });

            jobThread.start();
        } else {
            job.run();
        }
    }

    public String getName()
    {
        return name;
    }

    public String getQuery()
    {
        return query;
    }

    public void setException(Exception exception)
    {
        if (failure != null) return;
        failure = new Failure(id, name, query, dateAdded, dateStarted, new Date(), exception);
    }

    public void addResultPart(Tuple tuple, boolean last)
    {
        if (success == null) {
            success = new Success(id, name, query, dateAdded, dateStarted, new Date(), chartInfo);
        }
        this.success.addResult(tuple);
    }


    public void setProgress(int progress)
    {
        this.progress = progress;
    }

    public int getProgress()
    {
        return progress;
    }

    public Date getDateAdded()
    {
        return dateAdded;
    }

    public Date getDateStarted()
    {
        return dateStarted;
    }

    public int getId()
    {
        return id;
    }

    public Success getSuccess()
    {
        return success;
    }

    public boolean isDeleteResult()
    {
        return deleteResult;
    }

    public void setDeleteResult(boolean deleteResult)
    {
        this.deleteResult = deleteResult;
    }

//    public PigStats getPigStats() {
//        return pigStats;
//    }
}
