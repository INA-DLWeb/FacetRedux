package fr.ina.dlweb.proprioception.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.log4j.Logger;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.Tuple;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Date: 07/12/12
 * Time: 13:30
 *
 * @author drapin
 */
public class Success extends Result {
    private static final Logger log = Logger.getLogger(Success.class);

    private final List<String> results;
    private ChartInfo chartInfo;

    @JsonCreator
    public Success(
            @JsonProperty("id") int id,
            @JsonProperty("name") String name,
            @JsonProperty("query") String query,
            @JsonProperty("added") Date added,
            @JsonProperty("started") Date started,
            @JsonProperty("done") Date done,
            @JsonProperty("chart-info") ChartInfo chartInfo,
            @JsonProperty("results") List<String> results
    ) {
        super(id, name, query, added, started, done);
        this.chartInfo = chartInfo;
        this.results = results;
    }

    public Success(int id, String name, String query, Date added, Date started, Date done, ChartInfo chartInfo) {
        super(id, name, query, added, started, done);
        this.chartInfo = chartInfo;
        this.results = new ArrayList<String>();
    }

    public List<String> getResults() {
        return results;
    }

    public ChartInfo getChartInfo()
    {
        return chartInfo;
    }

    @JsonIgnore
    public void addResult(Tuple tuple) {
        try {
            if (tuple == null) return;
            results.add(tuple.toDelimitedString(","));
        } catch (ExecException e) {
            log.error(null, e);
        }
    }


    public void setChartInfo(ChartInfo chartInfo)
    {
        this.chartInfo = chartInfo;
    }
}
