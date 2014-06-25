package fr.ina.dlweb.proprioception.facetRedux.job;

import fr.ina.dlweb.daff.DAFFConstants;
import fr.ina.dlweb.daff.MetadataJSONContent;
import fr.ina.dlweb.daff.Record;
import fr.ina.dlweb.daff.RecordHeader;
import fr.ina.dlweb.hadoop.HadoopClientCDH4;
import fr.ina.dlweb.hadoop.io.StreamableDAFFInputFormat;
import fr.ina.dlweb.hadoop.io.StreamableDAFFRecordWritable;
import fr.ina.dlweb.mapreduce.MapReduceClientCDH4;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;

import java.io.IOException;
import java.util.Map;

/**
 * Date: 09/01/14
 * Time: 17:33
 *
 * @author drapin
 */
public class FacetReduxJobDAFF extends FacetReduxJob
{
    protected void configureInput(Job j, Path input) throws Exception
    {
        j.setInputFormatClass(StreamableDAFFInputFormat.class);
        FileInputFormat.setInputPaths(j, input);
    }

    @Override
    protected Class<? extends FacetReduxMapper> getMapperClass()
    {
        return HM2.class;
    }

    public static final class HM2 extends FacetReduxMapper<BytesWritable, StreamableDAFFRecordWritable>
    {
        private RecordHeader record = null;

        @Override
        protected Map<String, String> getMetadataFields(
            BytesWritable inputKey, StreamableDAFFRecordWritable inputValue, Context context
        ) throws IOException
        {
            record = inputValue.get();

            // record is instance of Record, ignore
            if (!(record instanceof Record)) {
                context.getCounter("proprioception", "wrong_record_class").increment(1);
                return null;
            }

            // record is not a metadata in JSON, ignore
            if (record.type() != DAFFConstants.METADATA_JSON_TYPE_FLAG) {
                context.getCounter("proprioception", "wrong_content_class").increment(1);
                return null;
            }

            return ((MetadataJSONContent) ((Record) record).content()).getMetadata(
                true, META_KEYS
                // "date", "crawl_session", "content", "url", "length", "status", "level", "type"
            );
        }
    }

    public static void main(String[] args) throws Exception
    {
        String year = "2013";
        args = new String[]{
            //"-input", "/user/public/dlweb-archive/metadata/data-sample/*",
            "-input=/user/public/dlweb-archive/metadata/data/*",
            "-output=/user/public/proprioception_job/" + year + "/" + System.currentTimeMillis() + "",
            "-reducers=7",
            "-year=" + year
        };

        HadoopClientCDH4 client = getHadoopClient();
        client.fetchConfig();

        MapReduceClientCDH4 mrClient = new MapReduceClientCDH4(client);
        mrClient.runTool(
            FacetReduxJobDAFF.class,
            args,
            new String[]{
                "./target/proprioception-web-0.2-SNAPSHOT-jar-with-dependencies.jar"
            }
        );
    }

    public static HadoopClientCDH4 getHadoopClient()
    {
        HadoopClientCDH4 c = new HadoopClientCDH4(
            "thaumas.ina.fr",
            7180,
            30,
            "public",
            "./hadoop-config-cache"
        );
        c.fetchConfig();
        return c;
    }
}
