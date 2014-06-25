package fr.ina.dlweb.proprioception.proprioPig;

import fr.ina.dlweb.pig.PigResultListener;
import fr.ina.dlweb.proprioception.models.PigJob;
import org.apache.pig.data.Tuple;

/**
 * Date: 07/12/12
 * Time: 14:43
 *
 * @author drapin
 */
public abstract class JobResultListener extends PigResultListener
{
    private PigJob job;

    protected JobResultListener(PigJob job, boolean deleteResult)
    {
        super(deleteResult);
        this.job = job;
    }

    @Override
    public final void onResult(Tuple tuple, boolean last)
    {
        job.addResultPart(tuple, last);
    }

    @Override
    public final void onError(Exception e)
    {
        job.setException(e);
    }

    @Override
    public final void progressUpdatedNotification(String scriptId, int progress)
    {
        super.progressUpdatedNotification(scriptId, progress);
        job.setProgress(progress);
    }

//    @Override
//    protected void setPigStats(PigStats pigStats) {
//        super.setPigStats(pigStats);
//        //job.pigStats = pigStats;
//    }

    @Override
    protected final void onDone()
    {
        onDone(job);
    }

    protected abstract void onDone(PigJob job);
}
