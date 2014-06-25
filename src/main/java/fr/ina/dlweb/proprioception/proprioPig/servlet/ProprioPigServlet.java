package fr.ina.dlweb.proprioception.proprioPig.servlet;

import fr.ina.dlweb.proprioception.proprioPig.ProprioPigApp;
import fr.ina.dlweb.proprioception.models.ChartInfo;
import fr.ina.dlweb.proprioception.models.PigScript;
import fr.ina.dlweb.proprioception.utils.FreeMarkerServlet;
import org.apache.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Date: 13/11/12
 * Time: 15:39
 *
 * @author drapin
 */
public class ProprioPigServlet extends FreeMarkerServlet
{
    private static final Logger log = Logger.getLogger(ProprioPigServlet.class);

    private ProprioPigApp app;
    private final int defaultReduceParallelism;
    private final boolean oldCluster;
    public final String externalPrefix = "/ext/";

    public ProprioPigServlet(int defaultReduceParallelism, boolean oldCluster)
    {
        super("jobs");
        this.defaultReduceParallelism = defaultReduceParallelism;
        this.oldCluster = oldCluster;
    }

    @Override
    public void init() throws ServletException
    {
        try {
            if (oldCluster) {
                app = new ProprioPigApp(true, "cluster_config_zero", true);
            } else {
                app = new ProprioPigApp(true, "cluster_config_one", true);
            }
            app.setDefaultReduceParallelism(defaultReduceParallelism);
        } catch (Exception e) {
            log.error(null, e);
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        String actionName = request.getParameter("a");
        if ("save".equals(actionName)) {
            app.saveStore();
            response.sendRedirect(request.getRequestURI());
            return;
        }

        String path = request.getPathInfo();
        if (path != null && path.startsWith(externalPrefix)) {
            String resource = path.substring(externalPrefix.length());
            respondResource(response, new File("./" + resource).toURI().toURL());
            return;
        }

        super.doGet(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        String actionName = request.getParameter("a");
        String query = request.getParameter("q");
        String scriptName = request.getParameter("n");
        String newScriptName = request.getParameter("nn");
        String targetHash = null;

        ChartInfo chartInfo = parseChartInfo(request);

        if ("addScript".equals(actionName)) {
            PigScript s = app.addPigScript(scriptName, query, chartInfo);
            targetHash = "script_" + s.getId();
        } else if ("startJob".equals(actionName)) {
            if (query != null) {
                app.startPigJob(scriptName, query, chartInfo);
            } else {
                app.startPigJob(scriptName, true);
            }
        } else if ("updateScript".equals(actionName)) {
            PigScript s = app.updatePigScript(scriptName, query, newScriptName, chartInfo);
            targetHash = "script_" + s.getId();
        } else if ("updateResult".equals(actionName)) {
            String resultId = request.getParameter("jid");
            app.updateResult(Integer.parseInt(resultId), chartInfo);
            targetHash = "job_" + resultId;
        } else if ("deleteResult".equals(actionName)) {
            String resultId = request.getParameter("jid");
            app.deleteResult(Integer.parseInt(resultId));
        } else if ("deleteScript".equals(actionName)) {
            app.deleteScript(scriptName);
        }

        response.sendRedirect(
            request.getRequestURI() + (targetHash == null ? "" : "#" + targetHash)
        );
    }

    private ChartInfo parseChartInfo(HttpServletRequest request)
    {
        String chartXType = request.getParameter("cxt");
        String chartSort = request.getParameter("csc");
        String chartX = request.getParameter("cx");
        String chartY = request.getParameter("cy");
        String chartKey = request.getParameter("ck");
        String chartType = request.getParameter("ct");
        String chartTransform = request.getParameter("ctr");
        return ChartInfo.create(chartXType, chartSort, chartX, chartY, chartKey, chartType, chartTransform);
    }

    @Override
    protected Map<String, Object> getModel(
        HttpServletRequest request, HttpServletResponse response, String templateName
    ) throws Exception
    {
        Map<String, Object> m = super.getModel(request, response, templateName);

        if ("jobs".equals(templateName)) {
            m.put("currentJob", app.getCurrentJob());
            m.put("jobQueueSize", app.getJobQueueSize());
            m.put("results", app.getResults());

        } else if ("scripts".equals(templateName)) {
            m.put("scripts", app.getScripts());
            m.put("footer", app.getFooter());

        } else if ("results.csv".equals(templateName)) {
            String jobId = request.getParameter("jid");
            response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"results_" + jobId + ".csv\""
            );

            m.put("result", app.getResult(Integer.parseInt(jobId)));

        }
        // else { nothing }

        return m;
    }
}
