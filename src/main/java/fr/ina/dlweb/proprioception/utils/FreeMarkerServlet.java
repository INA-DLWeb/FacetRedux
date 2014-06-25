package fr.ina.dlweb.proprioception.utils;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

/**
 * Date: 12/11/12
 * Time: 19:15
 *
 * @author drapin
 */
@SuppressWarnings({"UnusedDeclaration"})
public abstract class FreeMarkerServlet extends HttpServlet
{

    private final Configuration freeMarkerConfig;
    private final String defaultResource;

    public FreeMarkerServlet()
    {
        this("index.html");
    }

    public FreeMarkerServlet(String defaultResource)
    {
        this.defaultResource = defaultResource;

        freeMarkerConfig = new Configuration();
        freeMarkerConfig.setDefaultEncoding("UTF-8");
        freeMarkerConfig.setTemplateLoader(new ClassTemplateLoader(
            getClass(), ""
        ));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        doGet(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        // clean request
        String requestPath = req.getPathInfo();
        if (requestPath == null) requestPath = "";
        requestPath = requestPath.replaceFirst("^/", "");

        // redirect to default resource if empty path
        if (requestPath.isEmpty()) {
            resp.sendRedirect(req.getServletPath() + "/" + defaultResource);
            return;
        }

        get(requestPath, req, resp);
    }

    protected void get(String requestPath, HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        URL resource = getClass().getResource(requestPath);
        if (resource != null) {
            respondResource(resp, resource);
        } else {

            resource = getClass().getResource(requestPath + ".template");
            if (resource != null) {
                try {
                    respondTemplate(
                        resp.getWriter(),
                        getModel(req, resp, requestPath),
                        requestPath + ".template"
                    );
                } catch (Exception e) {
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

    protected void respondResource(HttpServletResponse resp, URL resource) throws IOException
    {
        if (resp.isCommitted()) {
            System.out.println("already commited : " + resource);
            return;
        }

        File file = new File(resource.getFile());
        //resp.setContentLength(file.length());
        String contentType = URLConnection.guessContentTypeFromName(file.getName());
        if (contentType != null) resp.setContentType(contentType);

        try {
            //System.out.println(">" + resource.getFile());
            OutputStream output = resp.getOutputStream();
            InputStream resourceStream = resource.openStream();
            IOUtils.copy(resourceStream, output);
            resourceStream.close();
            output.close();
        } catch (FileNotFoundException e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (Exception e) {
            //e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    protected Map<String, Object> getModel(HttpServletRequest request, HttpServletResponse resp, String templateName)
        throws Exception
    {
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("uri", request.getRequestURI());
        model.put("basePath", request.getServletPath());
        model.put("path", request.getPathInfo());
        model.put("remoteAddress", request.getRemoteAddr());
        model.put("remotePort", request.getRemotePort());
        model.put("localAddress", request.getLocalAddr());
        model.put("localPort", request.getLocalPort());
        model.put("params", getParamMap(request));
        return model;
    }

    private Map<String, String> getParamMap(HttpServletRequest request)
    {
        Map<String, String> m = new HashMap<String, String>();
        for (Object o : request.getParameterMap().entrySet()) {
            Map.Entry e = (Map.Entry) o;
            m.put((String) e.getKey(), ((String[]) e.getValue())[0]);
        }
        return m;
    }

    protected void respondTemplate(Writer writer, Map<String, Object> model, String templateName) throws IOException
    {
        Template template = freeMarkerConfig.getTemplate(templateName);
        try {
            template.process(model, writer);
        } catch (TemplateException e) {
            throw new RuntimeException("template processing failed", e);
        }
    }
}
