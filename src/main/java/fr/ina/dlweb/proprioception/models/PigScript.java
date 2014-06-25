package fr.ina.dlweb.proprioception.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.ina.dlweb.pig.PigClient;
import org.apache.pig.PigServer;

import java.util.Date;

/**
 * An script that can be run (query, chart info).
 *
 * Date: 27/11/12
 * Time: 14:58
 *
 * @author drapin
 */
public class PigScript extends PigTask implements Comparable<PigScript>
{
    private String name;
    private String query;
    private ChartInfo chartInfo;
    private PigServer parsedQuery;

    @JsonCreator
    public PigScript(
        @JsonProperty("name") String name,
        @JsonProperty("query") String query,
        @JsonProperty("failure") Failure failure,
        @JsonProperty("chart-info") ChartInfo chartInfo
    )
    {
        super(failure);
        this.name = name;
        this.query = query;
        this.chartInfo = chartInfo;
    }

    public PigScript(String name, String query, PigClient client, ChartInfo chartInfo)
    {
        this.name = name;
        if (name == null || name.isEmpty()) {
            throw new RuntimeException("script name cannot be null or empty");
        }
        this.chartInfo = chartInfo;
        this.query = query;
        parseQuery(client);
    }

    private void parseQuery(PigClient client)
    {
        failure = null;
        try {
            if (client == null) return;
            parsedQuery = parseQuery(query, client);
            parsedQuery.setJobName(name);
        } catch (Exception e) {
            failure = new Failure(-1, name, query, new Date(), new Date(), new Date(), e);
        }
    }

    @JsonIgnore
    public String getId()
    {
        return getName().replaceAll("[^A-Za-z0-9]+", "_");
    }

    @JsonIgnore
    public PigServer getParsedQuery()
    {
        return parsedQuery;
    }

    public String getQuery()
    {
        return query;
    }

    public String getName()
    {
        return name;
    }

    public ChartInfo getChartInfo()
    {
        return chartInfo;
    }

    @Override
    public int compareTo(PigScript o)
    {
        return getName().compareTo(o.getName());
    }

    public static PigServer parseQuery(String query, PigClient client)
    {
//        ProprioPigApp.patchPackageImportList();
        return client.getPigServer(query);
    }
}
