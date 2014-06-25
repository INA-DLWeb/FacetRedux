package fr.ina.dlweb.proprioception.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Date: 07/12/12
 * Time: 13:56
 *
 * @author drapin
 */
public class Failure extends Result {
    public static final Pattern EX_LINE_COL = Pattern.compile("<line (\\d+), column (\\d+)>");

    private final String message;
    private String trace;
    @JsonIgnore
    private Integer errorLine;
    @JsonIgnore private Integer errorColumn;

    @JsonCreator
    public Failure(
            @JsonProperty("id") int id,
            @JsonProperty("name") String name,
            @JsonProperty("query") String query,
            @JsonProperty("added") Date added,
            @JsonProperty("started") Date started,
            @JsonProperty("done") Date done,
            @JsonProperty("message") String message,
            @JsonProperty("trace") String trace
    ) {
        super(id, name, query, added, started, done);
        this.message = message;
        _setTrace(trace);
    }

    public Failure(int id, String name, String query, Date added, Date started, Date done, Exception exception) {
        super(id, name, query, added, started, done);

        if (exception == null) {
            message = "unknown error";
            trace = "empty trace";
            return;
        }

        this.message = exception.getMessage();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(200);
        PrintWriter writer = new PrintWriter(bytes);
        exception.printStackTrace(writer);
        writer.close();
        _setTrace(bytes.toString());
    }

    @JsonIgnore
    public Integer getErrorLine() {
        return errorLine;
    }

    @JsonIgnore
    public Integer getErrorColumn() {
        return errorColumn;
    }

    public String getMessage() {
        return message;
    }

    public String getTrace() {
        return trace;
    }

    private void _setTrace(String trace) {
        this.trace = trace;

        if (trace != null) {
            Matcher m = EX_LINE_COL.matcher(trace);
            if (m.find()) {
                errorLine = Integer.parseInt(m.group(1));
                errorColumn = Integer.parseInt(m.group(2));
            }
        }
    }
}
