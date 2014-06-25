package fr.ina.dlweb.proprioception.proprioPig;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import fr.ina.dlweb.proprioception.models.*;
import org.apache.log4j.Logger;
import org.codehaus.jackson.annotate.JsonCreator;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Date: 27/11/12
 * Time: 14:55
 *
 * @author drapin
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PersistenceStore {
    private static final Logger log = Logger.getLogger(PersistenceStore.class);

    @JsonIgnore
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @JsonIgnore
    private final Stack<PigJob> jobQueue = new Stack<PigJob>();

    private final Map<Integer, Result> results;
    private final Map<String, PigScript> scripts;

    @JsonIgnore
    private int maxResultId = 0;

    @JsonCreator
    public PersistenceStore(
            @JsonProperty("results") Map<Integer, Result> results,
            @JsonProperty("scripts") Map<String, PigScript> scripts
    ) {
        this.results = results != null ? results : new HashMap<Integer, Result>();
        this.scripts = scripts != null ? scripts : new HashMap<String, PigScript>();

        // compute max result id
        for (Result r : this.results.values()) {
            if (r.getId() > maxResultId) maxResultId = r.getId();
        }
    }

    public PersistenceStore() {
        results = new HashMap<Integer, Result>();
        scripts = new HashMap<String, PigScript>();
    }

    public static PersistenceStore load(String storePath) {
        try {
            File storeFile = new File(storePath);
            if (!storeFile.exists()) return new PersistenceStore();

            return mapper.readValue(
                    storeFile,
                    PersistenceStore.class
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void save(String storePath) {
        try {
            // write data to tmp file
            File tmpFile = new File(storePath + ".tmp");
            mapper.writeValue(tmpFile, this);

            // remove old store
            File storeFile = new File(storePath);
            if (storeFile.exists()) storeFile.delete();

            // rename tmp file to store
            if (tmpFile.renameTo(storeFile)) {
                log.info("saved store to '" + storeFile.getAbsolutePath() + "'");
            } else {
                log.error("error while saving to '" + storeFile.getAbsolutePath() + "'");
            }

        } catch (Exception e) {
            log.error("error while saving", e);
            throw new RuntimeException(e);
        }
    }

    @JsonIgnore
    public PigScript getScript(String scriptName) {
        return scripts.get(scriptName);
    }

    @JsonIgnore
    public int getNextResultId() {
        return ++maxResultId;
    }

    public void enqueue(PigJob pigJob) {
        jobQueue.push(pigJob);
    }

    public PigJob dequeue() {
        if (jobQueue.empty()) return null;
        return jobQueue.pop();
    }

    public void addDone(Result result) {
        results.put(result.getId(), result);
    }

    public void addScript(PigScript pigScript) {
        if (scripts.containsKey(pigScript.getName())) {
            throw new RuntimeException("name already used");
        }
        scripts.put(pigScript.getName(), pigScript);
    }

    @JsonIgnore
    public List<Result> getResults() {
        List<Result> s = new ArrayList<Result>(results.values());
        Collections.sort(s);
        return s;
    }

    @JsonIgnore
    public int getJobQueueSize() {
        return jobQueue.size();
    }

    @JsonIgnore
    public List<PigScript> getScripts() {
        List<PigScript> s = new ArrayList<PigScript>(scripts.values());
        Collections.sort(s);
        return s;
    }

    public PigScript updateScript(PigScript oldScript, PigScript newScript) {
        if (oldScript != null) {
            scripts.remove(oldScript.getName());
        }
        addScript(newScript);
        return newScript;
    }

    @JsonIgnore
    public Result getResult(Integer id) {
        return results.get(id);
    }

    @JsonIgnore
    public void deleteScript(String scriptName) {
        scripts.remove(scriptName);
    }

    @JsonIgnore
    public void deleteResult(Integer resultId) {
        results.remove(resultId);
    }

    @JsonIgnore
    public void updateResult(Integer jobId, ChartInfo chartInfo)
    {
        Result r = results.get(jobId);
        if (r instanceof Success) {
            Success s = (Success) r;
            s.setChartInfo(chartInfo);
        }
    }
}
