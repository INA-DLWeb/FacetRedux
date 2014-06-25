package fr.ina.dlweb.proprioception.facetRedux.servlet;

import fr.ina.dlweb.proprioception.facetRedux.FacetReduxConnector;
import fr.ina.dlweb.proprioception.facetRedux.select.FilterOperator;
import fr.ina.dlweb.proprioception.facetRedux.select.FilterType;
import fr.ina.dlweb.proprioception.utils.FreeMarkerServlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * Date: 30/01/14
 * Time: 15:47
 *
 * @author drapin
 */
public class FacetServlet extends FreeMarkerServlet
{
    private final FacetReduxConnector impala;

    public FacetServlet(String impalaServer)
    {
        super("easy2000.html");
        impala = new FacetReduxConnector(impalaServer, null);
    }

    @Override
    protected void get(String requestPath, HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        if (requestPath.equals("data.csv") && req.getParameter("html") != null) {
            resp.setHeader("Content-Type", "text/html");
        }
        super.get(requestPath, req, resp);
    }

    @Override
    protected Map<String, Object> getModel(HttpServletRequest request, HttpServletResponse resp, String templateName)
        throws Exception
    {
        Map<String, Object> m = super.getModel(request, resp, templateName);

        if (templateName.equals("data.csv")) {
            String sql = request.getParameter("sql");
            //System.out.println("data_sql: " + sql);
            try {
                m.put("html", request.getParameter("html") != null);
                m.put("results", impala.csv(sql));
            } catch (SQLException e) {
                m.put("error", e.getMessage() + "");
                //e.printStackTrace();
            }
        }

        if (templateName.equals("import.html")) {
            String source = request.getParameter("source");
            if (source == null) source = "";
            String year = request.getParameter("year");
            if (year == null) year = "";

            if (source.isEmpty()) {
                m.put("message", "'source' is required");
                return m;
            }
            if (year.isEmpty()) {
                m.put("message", "'year' is required");
                return m;
            }
            if (!year.matches("^\\d+$")) {
                m.put("message", "'year' must be a number");
                return m;
            }
            impala.importYear(source, Integer.parseInt(year));
            m.put("message", "Import DONE");
        }

        if (templateName.equals("filter-edit.html")) {
            m.put("filters", FilterType.values());
            m.put("operators", FilterOperator.values());
        }

        return m;
    }
}
